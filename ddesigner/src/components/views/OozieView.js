import React, {Component} from 'react';
import {translate} from "react-i18next";
import resource from "../../Resource";
import {Input, Tabs, Col, Select, Form, Button, Avatar, Tooltip, Row, DatePicker} from 'antd'
import DisplayList from './DisplayList'
import update from 'immutability-helper'
import ReactTable from 'react-table'
//import _ from 'lodash'
import moment from 'moment'
import IconFA from 'react-fa'

const {TabPane} = Tabs
const {TextArea} = Input
const {RangePicker} = DatePicker

class OozieView extends Component {

    constructor(...args) {
        super(...args);
        this.state = {tableData: [], taskType: "Workflow all", startDate: moment().add(-7, 'd'), endDate: moment(),
            formatDate: "YYYY-MM-DD"}
        this.taskTypes = ["Workflow active", "Workflow all", "Coordinator active", "Coordinator all"]
    }


    propsChanged(props) {
    }

    componentDidMount() {
        this.propsChanged(this.props)
    }

    componentWillReceiveProps(nextProps) {
        if (nextProps.entity.name !== this.props.entity.name) {
            this.propsChanged(nextProps)
        }
    }

    readRejects() {
        this.state.tableData.forEach(t => {
            if (t.actionType === "spark") {
                this.readSparkRejects(t)
            }
        })
    }

    loadTasks() {
        let d1 = this.state.startDate._isValid === true ? moment(this.state.startDate).format(this.state.formatDate) : moment("1900-01-01", this.state.formatDate).format(this.state.formatDate)
        let d2 = this.state.endDate._isValid === true ? moment(this.state.endDate).format(this.state.formatDate) : moment("2900-01-01", this.state.formatDate).format(this.state.formatDate)
        let httpApi = this.props.entity.http + "/oozie/v2"
        let taskType = this.state.taskType.split(" ")[0]
        let taskStatus = this.state.taskType.split(" ")[1]
        let nameNode = this.props.entity.nameNode
        let user = this.props.entity.user
        let home = this.props.entity.home
        let args = {
            oozie: httpApi, taskType: taskType, taskStatus: taskStatus,
            nameNode: nameNode, user: user, home: home,
            server: JSON.stringify(this.props.entity)
        }
        let filter = Object.keys(args).map((key) => `${key}=${encodeURIComponent(args[key])}`).join('&')
        resource.query(`/admin2/tasks/${d1}/${d2}?${filter}`).then(tasks => {
            tasks = tasks.map(t => {
                var s1 = moment(t.startTime, "DD-MM-YYYY HH:mm:ss")
                if (s1._isValid === false) {
                    s1 = moment(new Date(t.startTime))
                }
                var s2 = moment(t.finishTime, "DD-MM-YYYY HH:mm:ss")
                if (s2._isValid === false) {
                    s2 = moment(new Date(t.finishTime))
                }

                t.startTime = s1.format('YYYY-MM-DD HH:mm:ss')
                t.finishTime = s2.format('YYYY-MM-DD HH:mm:ss')
                return t
            })

            this.setState({tableData: tasks}, () => this.readRejects())
        })
    }

    loadLogs(task) {
        if (task.oozielog !== undefined) {
            return
        }
        const taskIndex = this.state.tableData.findIndex(t => t.id === task.id)
        if (taskIndex < 0) {
            return
        }
        task.oozielog = ''
        resource.query(`/admin2/job/${this.props.entity.user}/${task.id}/oozielog`).then(oozielog => {
            this.setState({tableData: update(this.state.tableData, {[taskIndex]: {$merge: {oozielog}}})})
        })
    }

    readSparkLog(task) {
        if (task.logs !== undefined) {
            return
        }
        const taskIndex = this.state.tableData.findIndex(t => t.id === task.id)
        if (taskIndex < 0) {
            return
        }
        task.logs = []
        resource.query(`/admin2/job/${this.props.entity.user}/${task.id}/logs`).then(logs => {
            if (logs) {
                this.setState({
                    tableData: update(this.state.tableData, {
                        [taskIndex]: {
                            $merge: {
                                logs: logs.map(row => JSON.parse(row))
                            }
                        }
                    })
                })
            }
        })
    }

    readSparkParams(task) {
        if (task.runparams !== undefined) {
            return
        }
        const taskIndex = this.state.tableData.findIndex(t => t.id === task.id)
        if (taskIndex < 0) {
            return
        }
        task.runparams = ""
        resource.query(`/admin2/job/${this.props.entity.user}/${task.id}/runparams`).then(runparams => {
            if (runparams) {
                this.setState({
                    tableData: update(this.state.tableData, {
                        [taskIndex]: {
                            $merge: {
                                runparams: runparams.join("\r\n")
                            }
                        }
                    })
                })
            }
        })
    }

    incrementParentRejected(task, numRejected) {
        var parentIndex = this.state.tableData.findIndex(p => p.id === task.parent)
        if (parentIndex < 0) {
            return this.state.tableData
        }
        const parent = this.state.tableData[parentIndex]
        const tableData = this.incrementParentRejected(parent, numRejected)
        return update(tableData, {[parentIndex]: {$merge: {numRejected: numRejected + (parent.numRejected || 0)}}})
    }

    readSparkRejects(task) {
        if (task.rejects !== undefined) {
            return
        }
        const taskIndex = this.state.tableData.findIndex(t => t.id === task.id)
        if (taskIndex < 0) {
            return
        }
        task.rejects = []
        resource.query(`/admin2/job/${this.props.entity.user}/${task.id}/rejects`).then(rejects => {
            if (rejects && rejects.length > 0) {
                const numRejected = rejects.length
                let tableData = this.incrementParentRejected(task, numRejected)
                tableData = update(tableData, {
                    [taskIndex]: {
                        $merge: {
                            numRejected, rejects: rejects.map(row => JSON.parse(row))
                        }
                    }
                })
                this.setState({tableData})
            }
        })
    }

    renderLog(row) {
        const formItemLayout = {
            labelCol: {span: 4},
            wrapperCol: {span: 14},
        }
        return <Form onSubmit={e => e.preventDefault()} layout={"horizontal"}>
            <Form.Item label={"Application Id"} {...formItemLayout}><Input value={row.original.applicationId}
                                                                           readOnly={true}/></Form.Item>
            <Form.Item label={"App name"} {...formItemLayout}><Input value={row.original.applicationName}
                                                                     readOnly={true}/></Form.Item>
            <Form.Item label={"Time"} {...formItemLayout}><Input value={row.original.eventTimestamp}
                                                                 readOnly={true}/></Form.Item>
            <Form.Item label={"Type"} {...formItemLayout}><Input value={row.original.eventType}
                                                                 readOnly={true}/></Form.Item>
            <Form.Item label={"Severity"} {...formItemLayout}><Input value={row.original.severity}
                                                                     readOnly={true}/></Form.Item>
            <Form.Item label={"Message"} {...formItemLayout}><TextArea value={row.original.message}
                                                                       readOnly={true}/></Form.Item>
        </Form>
    }

    renderLogsGrid(task) {
        return (
            <ReactTable
                data={task.logs}
                columns=
                    {
                        [{
                            Header: 'App Id', accessor: 'applicationId'
                        }, {
                            Header: 'Time', accessor: 'eventTimestamp'
                        }, {
                            Header: 'Source', accessor: 'eventType'
                        }, {
                            Header: 'Severity', accessor: 'severity'
                        }, {
                            Header: 'Message', accessor: 'message'
                        }]
                    }
                showPagination={false}
                collapseOnDataChange={false}
                minRows={1}
                style={{width: '100%'}}
                SubComponent={(row) => this.renderLog(row)}
            />
        )
    }

    renderReject(row) {
        const formItemLayout = {
            labelCol: {span: 4},
            wrapperCol: {span: 14},
        }
        return <Form onSubmit={e => e.preventDefault()} layout={"horizontal"}>
            <Form.Item label={"Workflow Id"} {...formItemLayout}><Input value={row.original.workflowid}
                                                                        readOnly={true}/></Form.Item>
            <Form.Item label={"Application Id"} {...formItemLayout}><Input value={row.original.appid} readOnly={true}/></Form.Item>
            <Form.Item label={"Time"} {...formItemLayout}><Input value={row.original.ts} readOnly={true}/></Form.Item>
            <Form.Item label={"Class"} {...formItemLayout}><Input value={row.original.classname}
                                                                  readOnly={true}/></Form.Item>
            <Form.Item label={"Method"} {...formItemLayout}><Input value={row.original.methodname}
                                                                   readOnly={true}/></Form.Item>
            <Form.Item label={"Object"} {...formItemLayout}><TextArea autosize={{minRows: 2, maxRows: 10}}
                                                                      value={row.original.object}
                                                                      readOnly={true}/></Form.Item>
            <Form.Item label={"Exception"} {...formItemLayout}><TextArea autosize={{minRows: 2, maxRows: 10}}
                                                                         value={row.original.exception}
                                                                         readOnly={true}/></Form.Item>
        </Form>
    }

    renderRejectsGrid(task) {
        return (
            <ReactTable
                data={task.rejects}
                columns=
                    {
                        [{
                            Header: 'Id', accessor: 'appid'
                        }, {
                            Header: 'Time', accessor: 'ts'
                        }, {
                            Header: 'Method', accessor: 'methodname'
                        }, {
                            Header: 'Object', accessor: 'object'
                        }, {
                            Header: 'Exception', accessor: 'exception'
                        }]
                    }
                showPagination={false}
                collapseOnDataChange={false}
                minRows={1}
                style={{width: '100%'}}
                SubComponent={(row) => this.renderReject(row)}
            />
        )
    }

    renderTask(row) {
        const task = row.original
        const filesBrowserUtilUrl = this.props.entity.filesBrowserUtilUrl
        if (task.actionType !== "spark") {
            return null
        }
        if (task.actionType === "spark") {
            this.readSparkLog(task)
            this.readSparkParams(task)
        }
        const formItemLayout = {
            labelCol: {span: 4},
            wrapperCol: {span: 14},
        }
        return (
            <Row>
                <Col span={1}/>
                <Col span={22}>
                    <Tabs>
                        <TabPane key={"description"} tab={"Description"}>
                            <Form onSubmit={e => e.preventDefault()} layout={"horizontal"}>
                                <Form.Item label={"Id"} {...formItemLayout}><Input value={task.id}
                                                                                   readOnly={true}/></Form.Item>
                                <Form.Item label={"Name"} {...formItemLayout}><Input value={task.name} readOnly={true}/></Form.Item>
                                <Form.Item label={"Action type"} {...formItemLayout}><Input value={task.actionType}
                                                                                            readOnly={true}/></Form.Item>
                                <Form.Item label={"Start time"} {...formItemLayout}><Input value={task.startTime}
                                                                                           readOnly={true}/></Form.Item>
                                <Form.Item label={"Finish time"} {...formItemLayout}><Input value={task.finishTime}
                                                                                            readOnly={true}/></Form.Item>
                                <Form.Item label={"Duration"} {...formItemLayout}><Input value={task.duration}
                                                                                         readOnly={true}/></Form.Item>
                                <Form.Item label={"Folder"} {...formItemLayout}><Input value={task.appFolder}
                                                                                       readOnly={true}/></Form.Item>
                                <Form.Item label={"Config"} {...formItemLayout}><TextArea value={task.config}
                                                                                          readOnly={true}/></Form.Item>
                                <Form.Item label={"Home"} {...formItemLayout}><Input value={task.home} readOnly={true}/></Form.Item>
                                <Form.Item label={"User"} {...formItemLayout}><Input value={task.user} readOnly={true}/></Form.Item>
                                <Form.Item label={"URL"} {...formItemLayout}><a
                                    href={task.url}>{task.url}</a></Form.Item>
                                <Form.Item label={"External Id"} {...formItemLayout}><Input value={task.externalId}
                                                                                            readOnly={true}/></Form.Item>
                                <Form.Item label={"Files"} {...formItemLayout}>
                                    <Tooltip title={"Open HDFS task folder"}>
                                        <Button shape="circle" style={{border: 0}} onClick={() => {
                                            resource.query(`/admin2/job/${task.user}/${task.id}/appid`).then(appid => {
                                                window.open(`${filesBrowserUtilUrl}/${task.home}/${this.props.entity.user}/${appid[0]}/`, '_blank')
                                            })
                                        }}><IconFA name="folder-open"/></Button>
                                    </Tooltip>
                                </Form.Item>
                            </Form>
                        </TabPane>
                        <TabPane key={"parameters"} tab={"Parameters"}>
                            <TextArea autosize={{minRows: 2, maxRows: 10}} readOnly={true} value={task.runparams}/>
                        </TabPane>
                        <TabPane key={"logs"} tab={"Logs"}>
                            {this.renderLogsGrid(task)}
                        </TabPane>
                        <TabPane key={"rejects"} tab={"Rejects"}>
                            {this.renderRejectsGrid(task)}
                        </TabPane>
                    </Tabs>
                </Col>
            </Row>
        )
    }

    renderChild(row) {
        const task = row.original
        const data = this.state.tableData.filter((childrow) => {
            return childrow.parent === row.row.id
        })
        if (["WORKFLOW", "COORDINATOR", "COORDINATOR_ACTION"].includes(task.taskType)) {
            this.loadLogs(task)
        }
        return (
            <Row>
                <Col span={1}/>
                <Col span={23}>
                    <Tabs>
                        <TabPane key={"childTasks"} tab={"Child Tasks"}>
                            <Col span={1}/>
                            <Col span={23}>
                                <ReactTable className='-highlight'
                                            data={data}
                                            pageSize={data.length}
                                            columns=
                                                {
                                                    [{
                                                        Header: 'Name', accessor: 'name'
                                                    }, {
                                                        Header: 'id', accessor: 'id'
                                                    }, {
                                                        Header: 'Status', accessor: 'status',
                                                        className: 'centered-field'
                                                    }, {
                                                        Header: 'Start',
                                                        accessor: 'startTime',
                                                        className: 'centered-field'
                                                    }, {
                                                        Header: 'Finish',
                                                        accessor: 'finishTime',
                                                        className: 'centered-field'
                                                    }, {
                                                        Header: 'Duration', accessor: 'Duration',
                                                        className: 'centered-field',
                                                        Cell: row => (row.original.duration ? row.original.duration
                                                            .replace("00w", "")
                                                            .replace("00d", "")
                                                            .replace("0d", "")
                                                            .replace("00h", "")
                                                            .replace("00m", "") : null)
                                                    }, {
                                                        Header: 'Type', accessor: 'actionType',
                                                        className: 'centered-field'
                                                    }, {
                                                        Header: 'Rejects', accessor: 'numRejected',
                                                        className: 'centered-field'
                                                    }]
                                                }
                                            showPagination={false}
                                            SubComponent={row => this.renderTask(row)}
                                            collapseOnDataChange={false}
                                            minRows={1}
                                />
                            </Col>
                        </TabPane>
                        <TabPane key={"oozieLog"} tab={"Oozie Log"}>
                            <TextArea autosize={{minRows: 2, maxRows: 10}} readOnly={true} value={task.oozielog}/>
                        </TabPane>
                        <TabPane key={"config"} tab={"Config"}>
                            <TextArea autosize={{minRows: 2, maxRows: 10}} readOnly={true} value={task.config}/>
                        </TabPane>
                    </Tabs>
                </Col>
            </Row>
        )
    }

    render() {
        const {t} = this.props
        return (
            <div>
                <Row>
                    <Form layout={"inline"}>
                        <Form.Item label={"Task type"}>
                            <Select value={this.state.taskType} style={{width: "200px"}} onChange={taskType => {
                                this.setState({taskType})
                            }}>
                                {this.taskTypes.map(opt => <Select.Option key={opt} value={opt}>{opt}</Select.Option>)}
                            </Select>
                        </Form.Item>
                        <Form.Item label={"Dates"}>
                            <RangePicker
                                value={[this.state.startDate, this.state.endDate]}
                                onChange={(dates) => {
                                    const [startDate, endDate] = dates
                                    this.setState({startDate, endDate})
                                }}
                            />
                        </Form.Item>
                        <Form.Item>
                            <Tooltip title={t("refresh")}>
                                <Button shape={"circle"} style={{border: 0}} onClick={() => this.loadTasks()}>
                                    <Avatar className="avatar-button-tool-panel"
                                            src={"images/icon-core/refresh-modern.svg"}/>
                                </Button>
                            </Tooltip>
                        </Form.Item>
                    </Form>
                </Row>
                <Row>
                    <DisplayList
                        list={this.state.tableData.filter(row => !row.parent)}
                        storageId={"dd_OozieView.tasks"}
                        columns={
                            [
                                {
                                    Header: 'rt.OozieTask.attrs.name.caption', accessor: 'name', show: true
                                },
                                {
                                    Header: 'rt.OozieTask.attrs.id.caption', accessor: 'id', show: true
                                },
                                {
                                    Header: 'rt.OozieTask.attrs.status.caption', accessor: 'status', show: true,
                                    className: 'centered-field',
                                    getProps: (state, rowInfo, column) => {
                                        return {
                                            style: {
                                                color: rowInfo ? (rowInfo.row.status === "KILLED" ? 'red' : (rowInfo.row.numRejected ? '#B18904' : null)) : null
                                            }
                                        }
                                    }
                                },
                                {
                                    Header: 'rt.OozieTask.attrs.start.caption',
                                    accessor: 'startTime',
                                    className: 'centered-field',
                                    show: true
                                },
                                {
                                    Header: 'rt.OozieTask.attrs.finish.caption',
                                    accessor: 'finishTime',
                                    className: 'centered-field',
                                    show: true
                                },
                                {
                                    Header: 'rt.OozieTask.attrs.duration.caption', accessor: 'Duration',
                                    className: 'centered-field', show: true,
                                    Cell: row => (row.row._original.duration ? row.row._original.duration
                                        .replace("00w", "")
                                        .replace("00d", "")
                                        .replace("0d", "")
                                        .replace("00h", "")
                                        .replace("00m", "") : null)
                                },
                                {
                                    Header: 'rt.OozieTask.attrs.type.caption', accessor: 'taskType',
                                    className: 'centered-field', show: true
                                },
                                {
                                    Header: 'rt.OozieTask.attrs.rejects.caption', accessor: 'numRejected',
                                    className: 'centered-field', show: true,
                                    filterMethod: (filter, row) => {
                                        if (filter.value === true) {
                                            return row["numRejected"]
                                        }
                                        return true
                                    },
                                    Filter: ({filter, onChange}) =>
                                        <input type={"checkbox"} value={1} onChange={event => {
                                            onChange(event.target.checked)
                                        }
                                        }/>
                                }
                            ]
                        }
                        controlColumn={{
                            Cell: row => (
                                <div>
                                    <Tooltip title={t("cancel")}>
                                        <Button type="dashed" size="small" placement="" onClick={() => {
                                            resource.queryResponse(`/admin2/task/${row.original.id}/kill`, {method: 'put'}).then(responce => {
                                                resource.logInfo(responce.statusText)
                                            })
                                        }}>
                                            <Avatar className="button-avatar" src="images/icon-core/cannel.svg"
                                                    size={"small"}/>
                                        </Button>
                                    </Tooltip>
                                    <Tooltip title={t("run")}>
                                        <Button type="dashed" size="small" placement="" onClick={() => {
                                            resource.queryResponse(`/admin2/task/${row.original.id}/rerun`, {method: 'put'}).then(responce => {
                                                resource.logInfo(responce.statusText)
                                            })
                                        }}>
                                            <Avatar className="button-avatar" src="images/icon-core/lightning.svg"
                                                    size={"small"}/>
                                        </Button>
                                    </Tooltip>
                                </div>
                            ),
                            filterable: false,
                            sortable: false,
                            resizable: false,
                            width: 90
                        }}
                        SubComponent={row => this.renderChild(row)}
                    />
                </Row>
            </div>
        )
    }
}

export default translate()(OozieView);
