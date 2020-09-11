import 'react-table/react-table.css';
import React, {Component} from 'react';
import {translate} from 'react-i18next';
import {Icon, Modal, Button, Tooltip, Select} from 'antd';
import ReactTable from 'react-table';
import _ from 'lodash';

const Option = Select.Option

function ColorCell(params){
    return <div style={{ width: '100%', height: '100%', backgroundColor: params.row.colour }} />
}

class DisplayList extends Component {
    constructor(...args) {
        super(...args);
        this.state = {showOptions: false, selected: []}
    }

    handleTableChange(pagination, filters, sorter) {
        console.log(sorter);
    }

    load(storageId, defFunc) {
        if (storageId) {
            const value = localStorage.getItem(storageId)
            if (value) {
                return JSON.parse(value)
            }
        }
        return defFunc()
    }

    store(storageId, value) {
        if (storageId) {
            localStorage.setItem(storageId, JSON.stringify(value))
        }
    }

    checkColorColumn(columns){
        columns.forEach((col, idx) => {
            if(col.accessor === "colour"){
                columns[idx].Cell = ColorCell
            }
        })
    }

    componentDidMount() {
        const columns = this.getPropsColumns()
        let {storageId} = this.props
        const selected = this.load(storageId, () => columns.filter(c => c.show))
        this.setState({selected, columns})
        this.checkColorColumn(this.props.columns)
    }

    componentWillUnmount() {
        let {storageId} = this.props
        this.store(storageId, this.state.selected)
    }

    getPropsColumns() {
        const {t, columns} = this.props
        return columns.map(c=>Object.assign({}, c, {Header: t(c.Header, {ns: ['classes', 'common']}), indexKey: c.accessor, sortable: true}))
    }

    getColumns() {
        return [
            ...this.state.selected.map(c => _.merge({}, this.getPropsColumns().find(cb => c.accessor === cb.accessor), {
                show: true,
                width: c.width
            })),
            ...this.getPropsColumns().filter(c => this.state.selected.findIndex(sc => sc.accessor === c.accessor) < 0).map(c => _.merge({}, c, {show: false}))
        ]
    }

    getAllColumns() {
        const {t} = this.props
        return [
            ...this.getColumns(),
            _.merge({}, this.props.controlColumn, {
                Header: (props) => (
                    <div>
                        <Tooltip title={t("columns")}>
                            <Button size="small"
                                    onClick={e => this.setState({showOptions: !this.state.showOptions})}><Icon
                                type="setting"/></Button>
                        </Tooltip>
                    </div>
                )
            })
        ]
    }

    render() {
        const data = this.props.list || []
        const {t, SubComponent} = this.props

        return (
            <div>
                <Modal
                    title={t("columns")}
                    visible={this.state.showOptions}
                    onOk={() => this.setState({showOptions: !this.state.showOptions})}
                    onCancel={() => this.setState({showOptions: !this.state.showOptions})}
                >
                    <Select
                        mode="multiple"
                        placeholder="Please select"
                        onChange={(x) => {
                            this.setState({selected: this.getPropsColumns().filter(col => (x.includes(col.accessor)))},
                            ()=>{
                                this.store(this.props.storageId, this.state.selected)
                            })
                        }}
                        defaultValue={this.state.selected.map((c) => {
                            return c.accessor
                        })}
                        style={{width: '100%'}}
                    >
                        {this.getPropsColumns().map((ch) => <Option key={ch.accessor}>{ch.Header}</Option>)}
                    </Select>
                </Modal>

                {this.props.list &&
                <ReactTable className='-highlight'
                            collapseOnDataChange={false}
                            pageSize={data.length}
                            showPagination={false}
                            filterable={true}
                            defaultFilterMethod={(filter, row) => {
                                return _.get(row, filter.id, '').toString().toLowerCase().includes(filter.value.toLowerCase())
                            }}
                            data={data}
                            resizable={true}
                            previousText={t('previous')}
                            nextText={t('next')}
                            loadingText={t('loading')}
                            noDataText={t('nodata')}
                            pageText={t('page')}
                            ofText={t('of')}
                            rowsText={t('rows')}
                            columns={this.getAllColumns()}
                            SubComponent={SubComponent}
                            onResizedChange={
                                (newResized) => {
                                    this.setState({
                                        selected:
                                            this.state.selected.map(c=> {
                                                const resized = newResized.find(r=>c.accessor===r.id)
                                                if(resized != null) {
                                                    c.width = resized.value
                                                }
                                                return c
                                            })

                                    }, ()=>{
                                        this.store(this.props.storageId, this.state.selected)
                                    })
                                }
                            }
                />
                }
            </div>
        )
    }
}

export default translate()(DisplayList);
