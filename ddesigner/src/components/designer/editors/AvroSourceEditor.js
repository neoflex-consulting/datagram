import React, { Component } from 'react';
import PropTypes from 'prop-types';
import AceEditor from 'react-ace';
import 'brace/mode/sql';
import 'brace/theme/sqlserver';
import 'brace/ext/searchbox';
import { translate } from 'react-i18next'
import update from 'immutability-helper'
import { Form, Tooltip, Avatar, InputNumber, Checkbox, Button, Divider } from 'antd';
import { AgGridReact } from 'ag-grid-react';
import 'ag-grid/dist/styles/ag-grid.css';
import 'ag-grid/dist/styles/ag-theme-balham.css';
import resource from "./../../../Resource";
import _ from 'lodash';

class AvroSourceEditor extends Component {

    static propTypes = {
        entity: PropTypes.object,
        cellEntity: PropTypes.object
    }

    constructor(...args) {
        super(...args);
        this.state = {
            dontExplode: false,
            sessionId: null,
            queryResult: null,
            queryError: null
        }
    }

    runAvro() {
        const { sessionId, dontExplode } = this.state
        const entity = this.props.entity
        const cellEntity = this.props.cellEntity
        resource.call({
            dontExplode: dontExplode,
            e_id: entity.e_id,
            fileName: cellEntity.path,
            nodeName: cellEntity.name,
            outputType: "json",
            sampleSize: cellEntity.sampleSize,
            sessionId: sessionId !== null ? sessionId : null,
            _type_: entity._type_
        }, "expandAvroFile", {}).then(json => {
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
        const newFields = this.state.queryResult.schema.fields.map(fl => (
            { name: fl.name.replace(new RegExp("->", 'g'), "_"), jsonPath: fl.name, dataTypeDomain: this.convertFieldType(fl.type), _type_: 'etl.JSONSourceField' }))
        const outputPort = update(this.props.cellEntity.outputPort, { $merge: { fields: newFields } })
        this.props.updateNodeEntity(update(this.props.cellEntity, { $merge: { outputPort } }), this.props.cellEntity)
    }

    convertFieldType(type) {
        if (typeof (type) === 'object') {
            type = type.type
        }
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
        const rowData = result.data.map(row =>
            _.mapValues(row, v => _.isObject(v) ? JSON.stringify(v) : v)
        )
        return (
            <div style={{ height: '80vh', overflow: 'auto' }}>
                <div style={{ boxSizing: 'border-box', height: '80vh' }} className="ag-theme-balham">
                    <AgGridReact
                        columnDefs={columns}
                        rowData={rowData}
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
                    {queryResult && <Form.Item wrapperCol={{ span: 2, push: 14 }}>
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
                                this.runAvro()
                            }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/show-modern.svg"} />
                            </Button>
                        </Tooltip>
                    </Form.Item>
                    <Form.Item wrapperCol={{ span: 2, push: 4 }}>
                        <Tooltip placement="top" title={t(cellEntity._type_ + '.attrs.sampleSize.caption', { ns: 'classes' })}>
                            <InputNumber size="small" value={this.props.cellEntity.sampleSize} onChange={(value) => this.inputNumberOnChange(value)} />
                        </Tooltip>
                    </Form.Item>
                    <Form.Item wrapperCol={{ span: 2, push: 4 }}>
                        <Tooltip placement="top" title={t(cellEntity._type_ + '.attrs.donotExplode.caption', { ns: 'classes' })}>
                            <Checkbox
                                style={{ 'marginLeft': '22px' }}
                                checked={this.state.dontExplode}
                                onChange={value => {
                                    this.setState({ dontExplode: value.target.checked })
                                }}
                            />
                        </Tooltip>
                    </Form.Item>
                </Form>
                <Divider style={{marginTop: 0, marginBottom: 0}}/>
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
        )
    }
}

export default translate()(AvroSourceEditor);
