import React, {Component} from 'react';
import {translate} from 'react-i18next'
import {Tooltip, Input, Button, Table, Form, Avatar} from 'antd'
import moment from 'moment'
import reactStringReplace from 'react-string-replace'
import resource from "../Resource";
import update from 'immutability-helper'

const columns = [
    {
        title: 'Level',
        dataIndex: 'level',
        key: 'level',
        width: 80,
        filters: [{text: "ERROR", value: "ERROR"}],
        onFilter: (value, record) => record.level === value
    },
    {
        title: 'Timestamp',
        dataIndex: 'timestamp',
        key: 'timestamp',
        render: (text) => moment(text).format('YYYY-MM-DD hh:mm:ss'),
        width: 150
    },
    {title: 'Message', dataIndex: 'message', key: 'message'}
]

class Logs extends Component {
    constructor(...args) {
        super(...args)
        this.state = {
            autoscroll: true,
            filterStr: undefined,
            logs: resource.logControl.logs
        }
    }

    componentWillUnmount() {
        resource.setLogCB(undefined)
    }

    componentDidMount() {
        resource.setLogCB(event => this.logCallback(event))
    }

    componentDidUpdate() {
        if (this.state.autoscroll) {
            this.refs["end"].scrollIntoView({behavior: 'smooth'})
        }
    }

    match(str) {
        if (!this.state.filterStr) {
            return true
        }
        return str !== undefined && str !== null && str.toUpperCase().indexOf(this.state.filterStr.toUpperCase()) >= 0
    }

    getDataSource() {
        if (!this.state.filterStr || this.state.filterStr.length === 0) {
            return this.state.logs
        }
        return this.state.logs.filter(value =>
            this.match(value.message) || this.match(value.stacktrace)
        ).map(value => {
            return {
                ...value,
                message: reactStringReplace(value.message, this.state.filterStr, (match, i) => (
                    <span key={i} style={{color: 'red'}}>{match}</span>
                )),
                stacktrace: value.stacktrace && reactStringReplace(value.stacktrace, this.state.filterStr, (match, i) => (
                    <span key={i} style={{color: 'red'}}>{match}</span>
                ))
            }
        })
    }

    logCallback(info) {
        if (this.timer) {
            clearTimeout(this.timer)
        }
        this.timer = setTimeout(() => {
            this.setState(update(this.state, {
                logs: {$set: resource.logControl.logs}
            }))
            this.timer = undefined
        }, 1)
        if (this.state.autoscroll) {
            this.refs["end"].scrollIntoView({behavior: 'smooth'})
        }
    }

    render() {
        const {t} = this.props
        return (
            <div>
                <div ref={"begin"}></div>
                <Form layout={"inline"}>
                    <Form.Item>
                        <Tooltip title={t("scrolltoend")}>
                            <Button shape="circle" style={{border: 0}} onClick={() => {
                                this.setState({autoscroll: true})
                                this.refs["end"].scrollIntoView({behavior: 'smooth'})
                            }}><Avatar size="small" src='images/icon-core/drop-down.svg'/></Button>
                        </Tooltip>
                    </Form.Item>
                    <Form.Item>
                        <Tooltip title={t("clear")}>
                            <Button shape="circle" style={{border: 0}} onClick={() => {
                                resource.logControl.logs = []
                                this.setState({logs: []})
                            }}><Avatar size="small" src='images/icon-core/delete-modern.svg'/></Button>
                        </Tooltip>
                    </Form.Item>
                    <Form.Item>
                        <Input prefix={<Avatar className="avatar-button-smaller" size="small" src='images/icon-core/search.svg'/>}
                               onChange={e => this.setState({filterStr: e.target.value})}
                               value={this.state.filterStr}/>
                    </Form.Item>
                </Form>
                <Table
                    size="small"
                    pagination={false}
                    columns={columns}
                    expandedRowRender={record => record.stacktrace &&
                        <pre style={{margin: 0}}>{record.stacktrace}</pre>}
                    dataSource={this.getDataSource()}
                    rowClassName={(record) => record.stacktrace == null ? "hide-expand-icon" : ""}
                />
                <Tooltip title={t("scrolltobegin")}>
                    <Button shape="circle" style={{border: 0}} onClick={() => {
                        this.setState({autoscroll: false})
                        this.refs["begin"].scrollIntoView({behavior: 'smooth'})
                    }}><Avatar size="small" src='images/icon-core/drop-up.svg'/></Button>
                </Tooltip>
                <div ref={"end"}></div>
            </div>
        )
    }
}

export default translate()(Logs);
