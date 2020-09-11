import React, {Component} from 'react';
import {translate} from "react-i18next";
import resource from "../../Resource";
import {Button, Avatar, Tooltip, Row, Col, Input, Tabs} from 'antd'
import DisplayList from './DisplayList'
import update from 'immutability-helper'
import _ from 'lodash'
import LivyInterpreter from './LivyInterpreter'

const TabPane = Tabs.TabPane
const {TextArea} = Input

class LivyView extends Component {

    constructor(...args) {
        super(...args);
        this.state = {
            kind: "spark",
            interpreters: []
        }
        this.index = 0
    }

    loadServerInfo() {
        this.loadSessions()
        this.loadBatches()
        this.loadServerState()
    }

    loadSessions() {
        this.setState({sessions: []})
        resource.query(`/api/operation/MetaServer/rt/LivyServer/${this.props.entity.name}/getSessions`).then(json => {
            this.setState({
                sessions: json.result.sessions.map(s => Object.assign({statementsInfo: {}}, s))
            })
        })
    }

    loadBatches() {
        this.setState({batches: []})
        resource.query(`/api/operation/MetaServer/rt/LivyServer/${this.props.entity.name}/getBatches`).then(json => {
            this.setState({
                batches: json.result.sessions
            })
        })
    }

    propsChanged(props) {
        if (!!props.entity && !!props.entity.name) {
            this.loadServerInfo()
        }
    }

    componentDidMount() {
        this.propsChanged(this.props)
        this.add()
    }

    componentWillReceiveProps(nextProps) {
        if (nextProps.entity !== this.props.entity) {
            this.propsChanged(nextProps)
        }
    }

    renderSessionsCommandCell(row) {
        return (
            <div>
            </div>
        )
    }

    renderBatchesCommandCell(row) {
        return (
            <div>
            </div>
        )
    }

    loadStatements(session_id, session_idx) {
        this.setState(update(this.state, {
            sessions: {
                [session_idx]: {
                    statementsInfo: {
                        statements: {$set: []}
                    }
                }
            }
        }))
        resource.query(`/api/operation/MetaServer/rt/LivyServer/${this.props.entity.name}/getStatements?sessionId=${session_id}`).then(json => {
            this.setState(update(this.state, {
                sessions: {
                    [session_idx]: {
                        statementsInfo: {
                            statements: {$set: json.result.statements}
                        }
                    }
                }
            }))
        })
    }

    renderStatement(row) {
        const code = _.get(row.original, ['code'], '')
        const evalue = _.get(row.original, ['output', 'evalue'], '')
        const traceback = _.get(row.original, ['output', 'traceback'])
        const text_plain = _.get(row.original, ['output', 'data', "text/plain"], '')
        return (
            <Row>
                <Col span={1}/>
                <Col span={23}>
                    <TextArea style={{fontFamily: "monospace"}} readOnly={true} autosize={{minRows: 2, maxRows: 10}}
                              value={'=== CODE:\n' + code + '\n=== OUTPUT:\n' + evalue + (traceback ? '\n' + traceback : '') + text_plain}
                    />
                </Col>
            </Row>
        )
    }

    killSession(id) {
        resource.query(`/api/operation/MetaServer/rt/LivyServer/${this.props.entity.name}/deleteSession?sessionId=${id}`).then(json => {
            this.loadSessions()
            resource.logInfo(JSON.stringify(json))
        })
    }

    renderSession(row) {
        const session_id = row.row.id
        const session_idx = this.state.sessions.findIndex(session => session.id === session_id)
        if (session_idx < 0) return null
        if (!this.state.sessions[session_idx].statementsInfo.statements) {
            setTimeout(() => this.loadStatements(row.row.id, session_idx), 1)
        }
        return (
            <Row>
                <Col span={1}/>
                <Col>
                    <DisplayList
                        list={this.state.sessions.find(session => session.id === session_id).statementsInfo.statements}
                        storageId={"dd_" + this.props.activeObject._type_ + ".sessions.statements"}
                        columns={[
                            {Header: 'rt.LivyServer.attrs.id.caption', accessor: 'id', show: true},
                            {Header: 'rt.LivyServer.attrs.state.caption', accessor: 'state', show: true},
                            {Header: 'rt.LivyServer.attrs.code.caption', accessor: 'code', show: true},
                            {
                                Header: 'rt.LivyServer.attrs.status.caption', accessor: 'output.status', show: true,
                                Cell: (row) => row.value === "ok" ? <span style={{color: "green"}}>{row.value}</span> :
                                    <span style={{color: "red"}}>{row.value}</span>
                            },
                            {Header: 'rt.LivyServer.attrs.error.caption', accessor: 'output.evalue', show: true},
                            {
                                Header: 'rt.LivyServer.attrs.data.caption',
                                accessor: 'output.data[text/plain]',
                                show: true
                            }
                        ]}
                        controlColumn={{
                            Header: '',
                            accessor: 'e_id',
                            Cell: row => null,
                            filterable: false,
                            sortable: false,
                            resizable: false,
                            width: 36
                        }}
                        SubComponent={(row) => this.renderStatement(row)}
                    />
                </Col>
            </Row>
        )
    }

    loadBatchLog(batch_id, batch_idx) {
        resource.query(`/api/operation/MetaServer/rt/LivyServer/${this.props.entity.name}/getBatchLog?batchId=${batch_id}`).then(json => {
            this.setState({batches: update(this.state.batches, {[batch_idx]: {$merge: {extendedLog: json.result}}})})
        })
    }

    loadServerState(){
        resource.query(`/livy/state/${this.props.entity.name}`).then(json => {
                    this.setState({
                                    serverState: json
                                });
                                console.log("serverState : " + json)
                })
    }



    renderServerState(){
     return (
                <Row>
                    <Col>this.state.serverState</Col>

                </Row>
            )


    }

    renderBatch(row) {
        const batch_id = row.row.id
        const batch_idx = this.state.batches.findIndex(batch => batch.id === batch_id)
        if (batch_idx < 0) return null
        let log = row.original.extendedLog
        if (!log) {
            log = row.original.log
            this.setState({batches: update(this.state.batches, {[batch_idx]: {$merge: {extendedLog: []}}})})
            setTimeout(() => this.loadBatchLog(row.row.id, batch_idx), 1)
        }
        return (
            <Row>
                <Col span={1}/>
                <Col span={23}>
                    <TextArea style={{fontFamily: "monospace"}} readOnly={true} autosize={{minRows: 2, maxRows: 10}}
                              value={log.join("\n")}
                    />
                </Col>
            </Row>
        )
    }

    cancelBatch(id) {
        resource.query(`/api/operation/MetaServer/rt/LivyServer/${this.props.entity.name}/deleteBatch?batchId=${id}`).then(json => {
            this.loadBatches()
            resource.logInfo(JSON.stringify(json))
        })
    }

    remove = (targetKey) => {
        this.setState({interpreters: this.state.interpreters.filter(i => i.key !== targetKey)})
    }

    add = () => {
        ++this.index;
        this.setState({
            interpreters: [...this.state.interpreters, {
                key: "Interpreter" + this.index,
                tab: "Interpreter " + this.index
            }]
        })
    }

    render() {
        const {t} = this.props
        return (
            <Tabs
                defaultActiveKey="Sessions"
                type="editable-card"
                onEdit={(targetKey, action) => {
                    this[action](targetKey);
                }}
            >
                <TabPane tab="Sessions" key="Sessions" closable={false}>
                    <DisplayList
                        list={this.state.sessions}
                        storageId={"dd_" + this.props.activeObject._type_ + ".sessions"}
                        columns={[
                            {Header: 'rt.LivyServer.attrs.id.caption', accessor: 'id', show: true},
                            {Header: 'rt.LivyServer.attrs.appId.caption', accessor: 'appId', show: true},
                            {Header: 'rt.LivyServer.attrs.kind.caption', accessor: 'kind', show: true},
                            {Header: 'rt.LivyServer.attrs.state.caption', accessor: 'state', show: true}
                        ]}
                        controlColumn={{
                            Header: 'Commands',
                            accessor: 'e_id',
                            Cell: row => (
                                <div>
                                    {(!!row.original.appInfo.driverLogUrl &&
                                    <a href={row.original.appInfo.driverLogUrl}>Driver Log</a>) ||
                                    <span>Driver Log</span>}&nbsp;
                                    {(!!row.original.appInfo.sparkUiUrl &&
                                    <a href={row.original.appInfo.sparkUiUrl}>Spark UI</a>) ||
                                    <span>Spark UI</span>}&nbsp;
                                    <Tooltip title={t("delete")}>
                                        <Button type="dashed" size="small" placement="" onClick={() => {
                                            this.killSession(row.original.id)
                                        }}>
                                            <Avatar className="button-avatar" src="images/icon-core/delete-modern.svg"
                                                    size={"small"}/>
                                        </Button>
                                    </Tooltip>
                                </div>
                            ),
                            filterable: false,
                            sortable: false,
                            resizable: false,
                            width: 180
                        }}
                        SubComponent={row => this.renderSession(row)}
                    />
                </TabPane>
                <TabPane tab="Batches" key="Batches" closable={false}>
                    <DisplayList
                        list={this.state.batches}
                        storageId={"dd_" + this.props.activeObject._type_ + ".batches"}
                        columns={[
                            {Header: 'rt.LivyServer.attrs.id.caption', accessor: 'id', show: true},
                            {Header: 'rt.LivyServer.attrs.appId.caption', accessor: 'appId', show: true},
                            {Header: 'rt.LivyServer.attrs.state.caption', accessor: 'state', show: true}
                        ]}
                        controlColumn={{
                            Cell: row => (
                                <div>
                                    {(!!row.original.appInfo.driverLogUrl &&
                                    <a href={row.original.appInfo.driverLogUrl}>Driver Log</a>) ||
                                    <span>Driver Log</span>}&nbsp;
                                    {(!!row.original.appInfo.sparkUiUrl &&
                                    <a href={row.original.appInfo.sparkUiUrl}>Spark UI</a>) ||
                                    <span>Spark UI</span>}&nbsp;
                                    <Tooltip title={t("delete")}>
                                        <Button type="dashed" size="small" placement="" onClick={() => {
                                            this.cancelBatch(row.original.id)
                                        }}>
                                            <Avatar className="button-avatar" src="images/icon-core/delete-modern.svg"
                                                    size={"small"}/>
                                        </Button>
                                    </Tooltip>
                                </div>
                            ),
                            filterable: false,
                            sortable: false,
                            resizable: false,
                            width: 180
                        }}
                        SubComponent={row => this.renderBatch(row)}
                    />
                </TabPane>
                 <TabPane tab="Shared Sessions" key="Shared_Sessions" closable={false}>
                       <Row>
                       <Col>Server state: </Col>
                       </Row>
                       <Row>
                            <Col><TextArea style={{fontFamily: "monospace"}} readOnly={true} autosize={{minRows: 2, maxRows: 10}}
                                                               value={JSON.stringify(this.state.serverState)}/></Col>
                       </Row>
                 </TabPane>
                {this.state.interpreters.map((interpreter, index) => <TabPane tab={interpreter.tab}
                                                                              key={interpreter.key}>
                    <LivyInterpreter entity={this.props.entity}/>
                </TabPane>)}
            </Tabs>
        )
    }
}

export default translate()(LivyView);
