import React, { Component, Fragment } from 'react';
import PropTypes from 'prop-types';
import AceEditor from 'react-ace';
import 'brace/mode/sql';
import 'brace/theme/sqlserver';
import 'brace/ext/searchbox';
import { translate } from 'react-i18next'
import update from 'immutability-helper'
import { Row, Col, Form, Tooltip, Avatar, InputNumber, Modal, Button, Select, Divider } from 'antd';
import { AgGridReact } from 'ag-grid-react';
import 'ag-grid/dist/styles/ag-grid.css';
import 'ag-grid/dist/styles/ag-theme-balham.css';
import resource from "./../../../Resource";
import _ from 'lodash';
import SplitPane from 'react-split-pane';
import Pane from 'react-split-pane/lib/Pane';
import './../../../css/split-pane.css';
import { cupOfCoffee } from '../../../utils/consts';


class SqlEditor extends Component {

    static propTypes = {
        entity: PropTypes.object,
        cellEntity: PropTypes.object
    }

    constructor(...args) {
        super(...args);
        this.tableGrid = React.createRef();
        this.fieldGrid = React.createRef();
        this.state = {
            contextList: [],
            context: null,
            showTable: false,
            sessionId: null,
            statement: null,
            queryResult: null,
            queryError: null,
            queryTablesResult: [],
            queryViewsResult: [],
            queryFieldsResult: [],
            selectedRowKeys: [],
            selectedTable: null
        }
        this.splitterPosition = '50%'
    }

    createEditor() {
        return <AceEditor
            ref={"aceEditor"}
            focus={true}
            cursorStart={1}
            mode={'sql'}
            width={'100%'}
            height={'100%'}
            theme={'sqlserver'}
            fontSize={15}
            editorProps={{ $blockScrolling: false }}
            value={this.props.cellEntity.statement}
            onChange={newValue => this.editorOnChange(newValue)}
            showPrintMargin={false}
            debounceChangePeriod={500}
        />
    }

    editorOnChange(newValue) {
        this.props.updateNodeEntity(update(this.props.cellEntity, { $merge: { statement: newValue } }), this.props.cellEntity)
    }

    executeSql() {
        const { context } = this.state
        if (context) {
            const session = this.state.sessionId
            const entity = this.props.entity
            const cellEntity = this.props.cellEntity
            this.setState({ queryResult: null, queryError: cupOfCoffee })
            resource.call({
                sessionId: session !== null ? session : null,
                _type_: entity._type_,
                e_id: entity.e_id,
                sampleSize: cellEntity.sampleSize,
                nodeName: cellEntity.name,
                statement: cellEntity.statement,
                outputType: "json"
            }, "generateAndRunPart", {}).then(json => {
                if (!json.result.valueCount) {
                    this.setState({ queryResult: JSON.parse(json.result), session: json.sessionId, queryError: null })
                } else {
                    this.setState({ queryResult: null, queryError: this.getUsableErrorView(json.result.values[0]) })
                }
            }).catch(() => this.setState({ queryError: null }))
        } else {
            resource.logError('Context name is undefined')
        }
    }

    getUsableErrorView(queryError) {
        return queryError.status + '\n' + queryError.evalue + '\n' + queryError.traceback.join('')
    }

    applySql() {
        const newFields = this.state.queryResult.schema.fields.map(fl => ({ name: fl.name, dataTypeDomain: this.convertFieldType(fl.type), _type_: 'dataset.Field' }))
        const outputPort = update(this.props.cellEntity.outputPort, { $merge: { fields: newFields } })
        this.props.updateNodeEntity(update(this.props.cellEntity, { $merge: { outputPort } }), this.props.cellEntity)
    }

    convertFieldType(type) {
        if (type.indexOf('decimal') + 1) {
            return 'DECIMAL'
        }
        if (type === 'boolean') {
            return 'BOOLEAN'
        }
        if (type === 'integer') {
            return 'INTEGER'
        }
        if (type === 'long') {
            return 'LONG'
        }
        if (type === 'date') {
            return 'DATE'
        }
        if (type === 'time' || type === 'datetime' || type === 'timestamp') {
            return 'DATETIME'
        }
        if (type === 'binary') {
            return 'BINARY'
        }
        if (type === 'float') {
            return 'FLOAT'
        }
        if (type === 'double') {
            return 'DOUBLE'
        }
        if (type === 'struct') {
            return 'STRUCT'
        }
        if (type === 'array') {
            return 'ARRAY'
        }
        return "STRING"
    }

    tableViewRefresh() {
        const { context } = this.state
        if (context) {
            resource.query("/api/operation/MetaServer/rt/SoftwareSystem/" + context + "/refreshScheme").then(result => {
                resource.logInfo('Success refresh!')
                this.getTables()
            })
        } else {
            resource.logError('Context name is undefined')
        }
    }

    tableViewApply() {
        const { selectedRowKeys, selectedTable } = this.state
        const getUsableView = selectedRowKeys.map((row, index) => {
            if (index + 1 === selectedRowKeys.length) {
                return ('\n\t' + row.field + '\n')
            }
            return ('\n\t' + row.field)
        })
        const newStatement = 'select' + getUsableView + 'from ' + selectedTable[0].table
        this.props.updateNodeEntity(update(this.props.cellEntity, { $merge: { statement: newStatement } }), this.props.cellEntity)
        this.setState({ showTable: false })
    }

    tableViewCancel() {
        this.setState({ showTable: false })
    }

    getTables() {
        const { context } = this.state
        if (context) {
            this.setState({ showTable: true })
            resource.query("/api/teneo/select/select t.name, t.e_id from rt.SoftwareSystem ss join ss.scheme s join s.tables t where ss.name='" + context + "'?__orderby=name").then(
                result => {
                    this.setState({
                        queryTablesResult: result.map(tb => ({ table: tb[0], e_id: tb[1], key: tb[0] }))
                    })
                }
            )
            resource.query("/api/teneo/select/select t.name, t.e_id from rt.SoftwareSystem ss join ss.scheme s join s.views t where ss.name='" + context + "'?__orderby=name").then(
                result => {
                    this.setState({
                        queryViewsResult: result.map(tb => ({ table: tb[0], e_id: tb[1], key: tb[0] }))
                    })
                }
            )
        } else {
            resource.logError('Context name is undefined')
        }
    }

    getFields(target) {
        resource.query("/api/teneo/select/select c.name, c.e_id, type(c.dataType), cast(c.columnType as string) from rel.Table t join t.columns c where t.e_id=:e_id order by c.name?e_id=" + target[0].e_id).then(
            result => {
                this.setState({
                    queryFieldsResult: result.map(res => ({ field: res[0], type: res[2], key: res[0] }))
                })
                this.refs.fieldGrid.api.selectAll()
            }
        )
    }

    createDisplayList() {
        const result = this.state.queryResult
        const columns = result.schema.fields.map(fld => ({ headerName: fld.name, field: fld.name, sortingOrder: ["asc", "desc"] }))
        return (
            <div style={{ height: '100%' }}>
                <div style={{ boxSizing: 'border-box', height: '100%' }} className="ag-theme-balham">
                    <div style={{ height: '100%', width: '100%' }}>
                        <AgGridReact
                            columnDefs={columns}
                            rowData={result.data}
                            //domLayout={'autoHeight'}
                            enableColResize={'true'}
                            pivotHeaderHeight={'true'}
                            rowHeight={'25'}
                            rowStyle={{ lineHeight: '10px' }}
                            enableSorting={true}
                            sortingOrder={["desc", "asc", null]}
                            enableFilter={true}
                        />
                    </div>
                </div>
            </div>
        )
    }

    inputNumberOnChange(newValue) {
        this.props.updateNodeEntity(update(this.props.cellEntity, { $merge: { sampleSize: newValue } }), this.props.cellEntity)
    }

    onSelectionChanged() {
        let selectedRow = this.refs.tableGrid.api.getSelectedRows()
        this.getFields(selectedRow)
    }

    getContextList() {
        resource.query("/api/teneo/select/select%20type(e),e.e_id,e.name%20from%20etl.Context%20e%20?__orderby=name").then(
            result => {
                this.setState({
                    contextList: result.map(tb => ({ _type_: tb[0], e_id: tb[1], name: tb[2] }))
                })
            }
        )
    }

    componentDidMount() {
        const { cellEntity } = this.props
        this.getContextList()
        if (cellEntity.context) {
            this.setState({ context: cellEntity.context.name })
        }
    }

    render() {
        const { t, cellEntity } = this.props
        const { queryResult, selectedRowKeys, selectedTable, queryError, queryFieldsResult, contextList } = this.state
        const tables = _.concat(this.state.queryTablesResult, this.state.queryViewsResult)
        const Option = Select.Option;
        return (
            <Fragment>
                <Form layout={"inline"}>
                    <Form.Item wrapperCol={{ span: 2, push: 14 }}>
                        <Tooltip placement="top" title={t("run")}>
                            <Button id="run" shape="circle" style={{ border: 0 }} onClick={() => {
                                this.executeSql()
                            }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/arrow-right-modern.svg"} />
                            </Button>
                        </Tooltip>
                    </Form.Item>
                    {queryResult && <Form.Item wrapperCol={{ span: 2, push: 14 }}>
                        <Tooltip placement="top" title={t('apply')}>
                            <Button id="apply" shape="circle" style={{ border: 0 }} onClick={() => {
                                this.applySql()
                            }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/check-modern.svg"} />
                            </Button>
                        </Tooltip>
                    </Form.Item>}
                    <Form.Item wrapperCol={{ span: 2, push: 14 }}>
                        <Tooltip placement="top" title={t('table')}>
                            <Button id="table" shape="circle" style={{ border: 0 }} onClick={() => {
                                this.getTables()
                            }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/table-modern.svg"} />
                            </Button>
                        </Tooltip>
                    </Form.Item>
                    <Form.Item wrapperCol={{ span: 2, push: 4 }}>
                        <Tooltip placement="top" title={t(cellEntity._type_ + '.attrs.sampleSize.caption', { ns: 'classes' })}>
                            <InputNumber size="small" value={this.props.cellEntity.sampleSize} onChange={(value) => this.inputNumberOnChange(value)} />
                        </Tooltip>
                    </Form.Item>
                    <Form.Item wrapperCol={{ span: 2, push: 2 }}>
                        <Tooltip placement="top" title={t('etl.Context.caption_plural', { ns: 'classes' })}>
                            <Select
                                showSearch
                                size="small"
                                style={{ width: 200, marginLeft: 4 }}
                                value={cellEntity.context ? cellEntity.context.name : undefined}
                                //placeholder={cellEntity.context ? cellEntity.context.name : t('etl.Context.caption', { ns: 'classes' })}
                                optionFilterProp="children"
                                onChange={(value) => {
                                    this.setState({ context: value })
                                    const newContext = contextList.find(c => c.name === value)
                                    this.props.updateNodeEntity(update(this.props.cellEntity, { $merge: { context: newContext } }), this.props.cellEntity)
                                }}
                            >
                                {contextList && contextList.map(item => {
                                    return <Option value={item.name}>{item.name}</Option>
                                })}
                            </Select>
                        </Tooltip>
                    </Form.Item>
                </Form>
                <Divider style={{marginTop: 0, marginBottom: 0}}/>
                <div style={{ height: 'calc(100vh - 190px)' }}>
                    <SplitPane
                        split="horizontal"
                        primary="first"
                        onChange={(values) => {
                            this.refs.aceEditor.editor.resize()
                            this.refs.console && this.refs.console.editor.resize()
                            this.splitterPosition = values[1]
                        }}
                    >
                        <Pane style={{ height: '100%', width: '100%', overflow: 'auto' }}>
                            {this.createEditor()}
                        </Pane>
                        <Pane
                            initialSize={this.splitterPosition}
                            style={{ height: '100%', width: '100%', overflow: 'auto' }}
                        >
                            {queryResult ? this.createDisplayList() : undefined}
                            {queryError ? <AceEditor
                                ref={"console"}
                                mode={'scala'}
                                width={'100%'}
                                height={'100%'}
                                theme={'tomorrow'}
                                fontSize={15}
                                editorProps={{ $blockScrolling: Infinity }}
                                value={queryError}
                                showPrintMargin={false}
                                showGutter={false}
                                focus={false}
                                readOnly={true}
                                minLines={5}
                                highlightActiveLine={false}
                            /> : undefined}
                        </Pane>
                    </SplitPane>
                </div>
                <Modal
                    title={this.props.t("tables")}
                    visible={this.state.showTable}
                    onOk={this.handleOk}
                    onCancel={() => this.tableViewCancel()}
                    width={"50%"}
                    footer={[
                        <Button key="refresh" type="dashed" onClick={() => this.tableViewRefresh()}>{this.props.t("refresh")}</Button>,
                        selectedTable != null && selectedRowKeys.length > 0 ?
                            <Button key="submit" type="primary" onClick={() => this.tableViewApply()}>{this.props.t("apply")}</Button> : undefined,
                        <Button key="back" onClick={() => this.tableViewCancel()}>{this.props.t("cancel")}</Button>
                    ]}
                >
                    <Row gutter={8}>
                        <Col span={12}>
                            <div style={{ height: '65vh', overflow: 'auto' }}>
                                <div style={{ boxSizing: "border-box", height: "100%", width: "100%" }} className="ag-theme-balham">
                                    <AgGridReact
                                        ref={"tableGrid"}
                                        columnDefs={[{ headerName: 'Table', field: 'table', sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' } }]}
                                        rowData={tables}
                                        //domLayout={'autoHeight'}
                                        enableColResize={'true'}
                                        pivotHeaderHeight={'true'}
                                        enableSorting={true}
                                        sortingOrder={["desc", "asc", null]}
                                        enableFilter={true}
                                        rowSelection={'single'}
                                        onSelectionChanged={(test) => {
                                            this.onSelectionChanged()
                                            this.setState({ selectedTable: this.refs.tableGrid.api.getSelectedRows() })
                                        }}
                                    />
                                </div>
                            </div>
                        </Col>
                        <Col span={12}>
                            <div style={{ height: '65vh', overflow: 'auto' }}>
                                <div style={{ boxSizing: "border-box", height: "100%", width: "100%" }} className="ag-theme-balham">
                                    <AgGridReact
                                        ref={"fieldGrid"}
                                        columnDefs={[
                                            { headerName: 'Filed', field: 'field', sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' } },
                                            { headerName: 'Type', field: 'type', sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' } }
                                        ]}
                                        rowData={queryFieldsResult}
                                        //domLayout={'autoHeight'}
                                        enableColResize={'true'}
                                        pivotHeaderHeight={'true'}
                                        enableSorting={true}
                                        sortingOrder={["desc", "asc", null]}
                                        enableFilter={true}
                                        rowSelection={'multiple'}
                                        onSelectionChanged={() => {
                                            this.setState({
                                                selectedRowKeys: this.refs.fieldGrid.api.getSelectedRows()
                                            })
                                        }}
                                    />
                                </div>
                            </div>
                        </Col>
                    </Row>
                </Modal>
            </Fragment>
        )
    }

}

export default translate()(SqlEditor);
