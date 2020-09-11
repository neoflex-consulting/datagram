import React, { Component } from 'react';
import PropTypes from 'prop-types';
import AceEditor from 'react-ace';
import 'brace/mode/sql';
import 'brace/theme/sqlserver';
import 'brace/ext/searchbox';
import { translate } from 'react-i18next'
import update from 'immutability-helper'
import { Form, Tooltip, Avatar, InputNumber, Button, Divider } from 'antd';
import { AgGridReact } from 'ag-grid-react';
import 'ag-grid/dist/styles/ag-grid.css';
import 'ag-grid/dist/styles/ag-theme-balham.css';
import resource from "./../../Resource";
import SplitterLayout from 'react-splitter-layout';
import { cupOfCoffee } from './../../utils/consts';


class HiveView extends Component {

    static propTypes = {
        entity: PropTypes.object,
        cellEntity: PropTypes.object
    }

    constructor(...args) {
        super(...args);
        this.tableGrid = React.createRef();
        this.fieldGrid = React.createRef();
        this.state = {
            showTable: false,
            sessionId: null,
            statement: null,
            queryResult: null,
            queryError: null
        }
    }

    createEditor() {
        return <AceEditor
            mode={'sql'}
            width={''}
            height={'71vh'}
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
        const session = this.state.sessionId
        const entity = this.props.entity
        const cellEntity = this.props.cellEntity
        this.setState({queryResult: null, queryError: cupOfCoffee})
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
        }).catch( ()=> this.setState({queryError: null}) )
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
                            domLayout={'autoHeight'}
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

    render() {
        const {t, cellEntity} = this.props
        const { queryResult, queryError } = this.state

        return (
            <div style={{ height: 'calc(100vh - 149px)' }}>
                <SplitterLayout
                    customClassName='splitter-layout splitter-sql-editor'
                    percentage={true}
                    primaryIndex={0}
                    vertical={true}
                    primaryMinSize={15}
                    secondaryMinSize={15}
                    secondaryInitialSize={15}
                >
                    <div>
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
                    <Form.Item wrapperCol={{ span: 2, push: 4 }}>
                        <Tooltip placement="top" title={t(cellEntity._type_ + '.attrs.sampleSize.caption', { ns: 'classes' })}>
                            <InputNumber size="small" value={this.props.cellEntity.sampleSize} onChange={(value) => this.inputNumberOnChange(value)} />
                        </Tooltip>
                    </Form.Item>
                    </Form>
                    <Divider style={{marginTop: 0, marginBottom: 0}}/>
                        {this.createEditor()}
                    </div>
                    <div>
                        {queryResult ? this.createDisplayList() : undefined}
                        {queryError ? <AceEditor
                        mode={'scala'}
                        width={''}
                        height={'80vh'}
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
                    </div>
                </SplitterLayout>
            </div>
        )
    }

}

export default translate()(HiveView);
