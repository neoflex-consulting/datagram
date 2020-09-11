import React, {Component} from 'react';
import _ from 'lodash';
import Measure from 'react-measure'
import {translate} from "react-i18next";
import {Divider, Icon, Form, Select, TreeSelect} from 'antd'
import {
    MarkSeries,
    LineSeries,
    XYPlot,
    VerticalBarSeries,
    LineMarkSeries,
    VerticalGridLines,
    HorizontalGridLines,
    XAxis,
    YAxis,
    DiscreteColorLegend,
    Hint
} from 'react-vis'
import moment from 'moment'

const aggs = {
    FIRST: _.first,
    LAST: _.last,
    MIN: _.min,
    MAX: _.max,
    AVG: _.mean,
    SUM: _.sum,
    COUNT: (data) => data.filter(e => !!e).length,
    ONE: (data) => 1
}

function getRandomColor() {
    var letters = '0123456789ABCDEF'.split('');
    var color = '#';
    for (var i = 0; i < 6; i++) {
        color += letters[Math.floor(Math.random() * 16)];
    }
    return color;
}

const COLORS = Array(100).fill(undefined).map(u => getRandomColor())

function getColor(i) {
    return COLORS[i % COLORS.length]
}

function aggregate(data, agg) {
    return agg !== "NONE" ? aggs[agg](data) : null
}

function groupByX(data, agg) {
    const sorted = _.sortBy(data, r => r.x)
    const result = []
    let yList = []
    let xLast = undefined
    for (let r of sorted) {
        if (r.x === xLast) {
            yList.push(r.y)
        }
        else {
            if (xLast) {
                result.push({x: xLast, y: aggregate(yList, agg)})
            }
            xLast = r.x
            yList = [r.y]
        }
    }
    if (xLast) {
        result.push({x: xLast, y: aggregate(yList, agg)})
    }
    return result
}

class Graph extends Component {
    constructor(...args) {
        super(...args);
        this.state = {
            viewDim: {width: 600, height: 400},
            formDim: {width: 600, height: 400},
            collapsed: this.props.collapsed,
            columns: this.props.columns,
            data: this.props.data,
            prepared: undefined,
            dots: this.props.dots,
            xList: this.props.xList || [],
            gList: this.props.gList || [],
            yList: this.props.yList || []
        }
    }

    static getDerivedStateFromProps(props, state) {
        const newState = {}
        if (state) {
            if (state.data !== props.data) {
                newState.data = props.data
                newState.prepared = undefined
            }
        }
        return Object.keys(newState).length > 0 ? newState : null
    }

    getLineTitle(line) {
        return `${line.x}; ${line.y}${line.agg ? "_" + line.agg : ""}${line.g ? "; " + this.state.gList.join(",") + "=" + line.g : ""}`
    }

    isNumber(dataType) {
        return ["DECIMAL", "INTEGER", "LONG", "FLOAT", "DOUBLE", "BOOLEAN"].includes(dataType)
    }

    isTime(dataType) {
        return ["DATE", "DATETIME", "TIME"].includes(dataType)
    }

    xIsTime() {
        const converters = this.getConverters()
        for (let x of this.state.xList) {
            if (converters[x].isTime) {
                return true
            }
        }
        return false
    }

    getConverters() {
        return this.state.columns.reduce((converters, column, index)=>{
            let cnv = (v)=>(v)
            const dataType = column.columnType.dataType
            const isNumber = this.isNumber(dataType)
            const isTime = this.isTime(dataType)
            if (isNumber) {
                cnv = (v)=>Number(v)
            }
            else if (isTime) {
                cnv = (v)=>moment(v).toDate()
            }
            return {...converters, [column.columnName]: {cnv, isNumber, isTime}}
        }, {})
    }

    prepareData(data, xList, gList, yList) {
        const result = []
        const converters = this.getConverters()
        const groups = _.groupBy(data, r => gList.map(key => r[key]))
        for (let g of Object.keys(groups)) {
            const gData = groups[g]
            for (let x of xList) {
                for (let {y, agg} of yList) {
                    const line = {g, x, y, agg}
                    let xyData = gData.map(r => {
                        return {x: converters[x].cnv(r[x]), y: converters[y].cnv(r[y])}
                    })
                    if (agg) {
                        xyData = groupByX(xyData, agg)
                    }
                    else {
                        xyData = _.sortBy(xyData, 'x')
                    }
                    line.data = xyData
                    result.push(line)
                }
            }
        }
        return result
    }

    prepare() {
        this.setState({prepared: this.prepareData(this.state.data, this.state.xList, this.state.gList, this.state.yList, this.state.aggList)})

    }

    componentDidUpdate(prevProps) {
        if (this.state.prepared === undefined && this.state.data !== undefined && this.props.visible) {
            this.prepare()
        }
    }

    createLineChart() {
        const all = this.state.prepared.reduce((prev, curr, lineIndex) => {
            return [...prev, ...curr.data.map((d, pointIndex) => ({...d, lineIndex, pointIndex}))]
        }, [])
        return (
            <XYPlot width={this.state.viewDim.width} height={this.state.viewDim.height - this.state.formDim.height}
                    margin={{top: 10, right: 80, bottom: 50, left: 100}}
                    onMouseLeave={
                        () => {
                            this.setState({lineIndex: undefined, pointIndex: undefined, point: undefined})
                        }
                    }
            >
                <VerticalGridLines/>
                <HorizontalGridLines/>
                <XAxis xType={this.xIsTime() ? "time" : undefined}/>
                <YAxis/>
                {this.state.prepared.map((line, i) => (i === this.state.lineIndex &&
                    <LineSeries
                        key={"showSelectedLine" + i}
                        data={line.data}
                        curve={'curveMonotoneX'}
                        color="grey"
                        strokeWidth={5}
                    />
                ))}
                {this.state.prepared.map((line, i) =>
                    <LineMarkSeries
                        key={i}
                        data={line.data}
                        curve={'curveMonotoneX'}
                        color={getColor(i)}
                    />
                )}
                <MarkSeries
                    key={"selectPoint"}
                    data={all}
                    color="transparent"
                    size={10}
                    onNearestXY={(point, {index}) => {
                        const {lineIndex, pointIndex} = all[index]
                        this.setState({lineIndex, pointIndex, point})
                    }}
                />
                {this.state.prepared.map((line, i) => (i === this.state.lineIndex &&
                    <MarkSeries
                        key={"showSelectedPoint"}
                        data={line.data.filter((r, i) => i === this.state.pointIndex)}
                        color="black"
                        fill="white"
                        size={5}
                    />)
                )}
                {this.state.point !== undefined && this.state.lineIndex !== undefined &&
                <Hint key="hint" value={this.state.point} format={(point) => {
                    const line = this.state.prepared[this.state.lineIndex]
                    return [
                        {title: "Line", value: this.getLineTitle(line)},
                        {title: "Point", value: `(${point.x}, ${point.y})`},
                    ]
                }}/>}
            </XYPlot>
        )
    }

    createBarChart() {
        return (
            <XYPlot width={this.state.viewDim.width} height={this.state.viewDim.height - this.state.formDim.height}
                    margin={{top: 10, right: 80, bottom: 50, left: 100}}
                    onMouseLeave={() => this.setState({lineIndex: undefined, pointIndex: undefined, point: undefined})}
            >
                <VerticalGridLines/>
                <HorizontalGridLines/>
                <XAxis xType={this.xIsTime() ? "time" : undefined}/>
                <YAxis/>
                {this.state.prepared.map((line, i) =>
                    <VerticalBarSeries
                        key={i}
                        data={line.data}
                        stroke={i===this.state.lineIndex ? "grey" : getColor(i)}
                        style={{strokeWidth: 2}}
                        fill={getColor(i)}
                        onValueMouseOver={(point, event)=>{
                            const pointIndex = line.data.findIndex(p=>p.x===point.x && p.y===point.y)
                            this.setState({lineIndex: i, pointIndex, point})
                        }}
                        onValueMouseOut={(point, event)=>{
                            this.setState({lineIndex: undefined, pointIndex: undefined, point: undefined})
                        }}
                    />
                )}
                
                {this.state.point !== undefined && this.state.lineIndex !== undefined &&
                <Hint key="hint" value={this.state.point} format={(point) => {
                    const line = this.state.prepared[this.state.lineIndex]
                    return [
                        {title: "Bar", value: this.getLineTitle(line)},
                        {title: "Point", value: `(${point.x}, ${point.y})`},
                    ]
                }}/>}
            </XYPlot>
        )
    }

    render() {
        if (!this.props.visible) return false
        const formItemLayout = {
            labelCol: {span: 4},
            wrapperCol: {span: 14},
            style: {marginBottom: 0, marginTop: 0}
        }
        return <Measure
            bounds
            onResize={(viewRect) => {
                this.setState({viewDim: viewRect.bounds})
            }}
        >
            {({measureRef}) =>
                <div ref={measureRef} style={{width: "100%", height: "100%"}}>
                    <Measure
                        bounds
                        onResize={(formRect) => {
                            this.setState({formDim: formRect.bounds})
                        }}
                    >
                        {({measureRef}) =>
                            <div ref={measureRef}>
                                <Icon type={this.state.collapsed ? "down" : "up"} onClick={() => {
                                    this.setState({collapsed: !this.state.collapsed})
                                }}/>
                                {!this.state.collapsed &&
                                <Form layout="horizontal" style={{marginBottom: 10}}>
                                    <Form.Item label={"X"} {...formItemLayout}>
                                        <Select className="ant-select-no-padding" showSearch allowClear size="small"
                                                mode="multiple"
                                                style={{minWidth: 50}}
                                                value={this.state.xList}
                                                onChange={xList => {
                                                    this.setState({xList}, () => {
                                                        this.prepare()
                                                    })
                                                    if(this.props.saveGraphValue){
                                                        this.props.saveGraphValue({ axisX: xList })
                                                    }
                                                }}
                                                filterOption={(input, option) => option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0}
                                        >
                                            {_.sortBy(this.state.columns).map((column, index) =>
                                                <Select.Option key={index} value={column.columnName}>{column.columnName}</Select.Option>)}
                                        </Select>
                                    </Form.Item>
                                    <Form.Item label={"Groups"} {...formItemLayout}>
                                        <Select className="ant-select-no-padding" showSearch allowClear size="small"
                                                mode="multiple"
                                                style={{minWidth: 50}}
                                                value={this.state.gList}
                                                onChange={gList => {
                                                    this.setState({gList}, () => {
                                                        this.prepare()
                                                    })
                                                    if(this.props.saveGraphValue){
                                                        this.props.saveGraphValue({ groups: gList })
                                                    }
                                                }}
                                                filterOption={(input, option) => option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0}
                                        >
                                            {_.sortBy(this.state.columns).map((column, index) =>
                                                <Select.Option key={index} value={column.columnName}>{column.columnName}</Select.Option>)}
                                        </Select>
                                    </Form.Item>
                                    <Form.Item label={"Y"} {...formItemLayout}>
                                        <TreeSelect className="ant-select-no-padding" showSearch allowClear size="small"
                                                    mode="multiple"
                                                    style={{minWidth: 50}}
                                                    value={this.state.yList.map(r => ({
                                                        value: `${r.y}_${r.agg}`,
                                                        label: `${r.y}_${r.agg}`
                                                    }))}
                                                    onChange={values => {
                                                        this.setState({
                                                            yList: values.map(v => {
                                                                let arr = v.value.split('_')
                                                                const agg = arr.pop()
                                                                const y = arr.join('_')
                                                                return {y, agg}
                                                            })
                                                        }, () => {
                                                            this.prepare()
                                                        })
                                                        if(this.props.saveGraphValue){
                                                            const updatedArray = values.map(v=>
                                                                ({column: v.value.split('_')[0], func: v.value.split('_')[1].length > 0 ? v.value.split('_')[1] : "NONE", _type_: "sse.ColumnAgg"}))
                                                            this.props.saveGraphValue({ axisY: updatedArray })
                                                        }
                                                    }}
                                                    filterOption={(input, option) => option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0}
                                                    treeCheckable={true}
                                                    treeCheckStrictly={true}
                                                    labelInValue={true}
                                                    treeData={_.sortBy(this.state.columns).map((column) =>
                                                        ({
                                                            key: column.columnName,
                                                            title: column.columnName,
                                                            value: `${column.columnName}_`,
                                                            children: [
                                                                ...Object.keys(aggs).map(agg => ({
                                                                    key: `${column.columnName}_${agg}`,
                                                                    title: agg,
                                                                    value: `${column.columnName}_${agg}`
                                                                }))
                                                            ]
                                                        }))}
                                        />
                                    </Form.Item>
                                    {this.state.prepared && this.state.prepared.length > 0 && <DiscreteColorLegend
                                        orientation="horizontal"
                                        items={this.state.prepared.map((line, i) => ({
                                            title: this.getLineTitle(line),
                                            color: getColor(i)
                                        }))}
                                    />}

                                </Form>}
                                <Divider style={{marginTop: 0, marginBottom: 0}}/>
                            </div>
                        }
                    </Measure>
                    {
                        this.state.prepared && this.state.prepared.length > 0 && (
                            (this.props.view === "line-chart" && this.createLineChart()) ||
                            (this.props.view === "bar-chart" && this.createBarChart())
                        )
                    }
                </div>
            }
        </Measure>
    }
}

export default translate()(Graph);
