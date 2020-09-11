import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { translate } from 'react-i18next'
import { Form, Tooltip, InputNumber, Avatar, Button, Divider } from 'antd';
import NfDataGrid from './../NfDataGrid';
import 'ag-grid/dist/styles/ag-grid.css';
import 'ag-grid/dist/styles/ag-theme-balham.css';
import resource from "./../../Resource";
import _ from 'lodash';
import update from 'immutability-helper';
import AceEditor from 'react-ace';
import 'brace/mode/sql';
import 'brace/theme/sqlserver';
import 'brace/ext/searchbox';

class LocalView extends Component {

    static propTypes = {
        entity: PropTypes.object,
        cellEntity: PropTypes.object
    }

    constructor(...args) {
        super(...args);
        this.state = {
            showTable: false,
            sessionId: null,
            statement: null,
            queryResult: null,
            queryError: null
        }
    }

    getMethod(type) {
        if (type === 'etl.CSVTarget' || type === 'etl.LocalTarget' || type === 'etl.DeltaTarget') {
            return 'showContent'
        }
        return 'generateAndRunPart'
    }

    getObject() {
        const session = this.state.sessionId
        const { entity, cellEntity, context } = this.props
        if (cellEntity._type_ === 'etl.CSVTarget' || cellEntity._type_ === 'etl.LocalTarget' || cellEntity._type_ === 'etl.DeltaTarget') {
            return ({
                sessionId: session !== null ? session : null,
                _type_: cellEntity._type_,
                sampleSize: cellEntity.sampleSize,
                target: cellEntity,
                transformation: { e_id: context.entity.e_id, name: context.entity.name, _type_: context.entity._type_ },
                outputType: "json"
            })
        }
        return ({
            sessionId: session !== null ? session : null,
            _type_: entity._type_,
            e_id: entity.e_id,
            sampleSize: cellEntity.sampleSize,
            nodeName: cellEntity.name,
            outputType: "json"
        })
    }

    run() {
        const method = this.getMethod(this.props.cellEntity._type_)
        const object = this.getObject()
        resource.call(object, method, {}).then(json => {
            if (!json.result.valueCount) {
                this.setState({ queryResult: JSON.parse(json.result), session: json.sessionId, queryError: null })
            } else {
                this.setState({ queryResult: null, queryError: this.getUsableErrorView(json.result.values[0]) })
            }
        })
    }

    getUsableErrorView(queryError) {
        return queryError.status + '\n' + queryError.evalue + '\n' + queryError.traceback.join('')
    }

    save() {
        const newFields = this.state.queryResult.schema.fields.map(fl => {
            let domainStructure = this.convertFieldType(fl.type)
            let dataTypeDomain = domainStructure._type_ === "dataset.ScalarType" ? domainStructure.dataTypeDomain : null
            if (dataTypeDomain) {
                domainStructure = undefined
            }
            return {
                name: fl.name.replace(new RegExp("->", 'g'), "_"),
                domainStructure, dataTypeDomain,
                _type_: 'dataset.Field'
            }
        })
        const outputPort = update(this.props.cellEntity.outputPort, { $merge: { fields: newFields } })
        this.props.updateNodeEntity(update(this.props.cellEntity, { $merge: { outputPort } }), this.props.cellEntity)
    }

    convertScalarType(type) {
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

    convertFieldType(type) {
        if (typeof (type) === 'object') {
            if (type.type === 'struct') {
                return {
                    _type_: "dataset.StructType", internalStructure: {
                        _type_: "dataset.Structure",
                        fields: type.fields.map(f => {
                            let domainStructure = this.convertFieldType(f.type)
                            let dataTypeDomain = domainStructure._type_ === "dataset.ScalarType" ? domainStructure.dataTypeDomain : null
                            if (dataTypeDomain) {
                                domainStructure = undefined
                            }
                            return {
                                _type_: "dataset.Field", name: f.name, domainStructure, dataTypeDomain
                            }
                        })
                    }
                }
            } else if (type.type === 'array') {
                return {_type_: "dataset.ArrayType", elementType: this.convertFieldType(type.elementType)}
            } else {
                return undefined
            }
        } else {
            return {_type_: "dataset.ScalarType", dataTypeDomain: this.convertScalarType(type)}
        }
    }

    createDisplayList() {
        const result = this.state.queryResult
        const columns = result.schema.fields.map(fld => ({ headerName: fld.name, field: fld.name, sortingOrder: ["asc", "desc"] }))
        const rowData = result.data.map(row =>
            _.mapValues(row, v => _.isObject(v) ? JSON.stringify(v) : v)
        )
        return (
            <div style={{ boxSizing: 'border-box', height: 'calc(100vh - 192px)', width: '100%' }}>
                <NfDataGrid
                    ref={this.DataGrid}
                    columnDefs={columns}
                    rowData={rowData}
                    gridOptions={{
                        rowSelection: 'single',
                        rowMultiSelectWithClick: true,
                        onCellClicked: this.cellClick
                    }}
                />
            </div>
        )
    }

    inputNumberOnChange(newValue) {
        this.props.updateNodeEntity(update(this.props.cellEntity, { $merge: { sampleSize: newValue } }), this.props.cellEntity)
    }

    render() {
        const { t, cellEntity } = this.props
        const { queryResult, queryError } = this.state
        return (
            <div style={{ height: 'calc(100vh - 149px)' }}>
                <Form layout={"inline"}>
                    {queryResult && (cellEntity._type_ === 'etl.LocalSource' || cellEntity._type_ === 'etl.CSVSource') &&
                        <Form.Item wrapperCol={{ span: 2, push: 14 }}>
                            <Tooltip placement="top" title={t("save")}>
                                <Button id="save" shape="circle" style={{ border: 0 }} onClick={() => {
                                    this.save()
                                }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/save-modern.svg"} />
                                </Button>
                            </Tooltip>
                        </Form.Item>}
                    <Form.Item wrapperCol={{ span: 2, push: 14 }}>
                        <Tooltip placement="top" title={t('show')}>
                            <Button id="show" shape="circle" style={{ border: 0 }} onClick={() => {
                                this.run()
                            }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/show-modern.svg"} />
                            </Button>
                        </Tooltip>
                    </Form.Item>
                    <Form.Item wrapperCol={{ span: 2, push: 4 }}>
                        <Tooltip placement="bottom" title={t(cellEntity._type_ + '.attrs.sampleSize.caption', { ns: 'classes' })}>
                            <InputNumber size="small" value={this.props.cellEntity.sampleSize} onChange={(value) => this.inputNumberOnChange(value)} />
                        </Tooltip>
                    </Form.Item>
                </Form>
                <Divider style={{ marginTop: 0, marginBottom: 0 }} />
                {queryResult && this.createDisplayList()}
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
        )
    }

}

export default translate()(LocalView);
