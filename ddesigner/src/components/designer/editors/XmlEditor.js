import React, {Component} from 'react';
import PropTypes from 'prop-types';
import AceEditor from 'react-ace';
import 'brace/mode/sql';
import 'brace/theme/sqlserver';
import 'brace/ext/searchbox';
import {translate} from 'react-i18next'
import update from 'immutability-helper'
import {Form, Tooltip, Avatar, InputNumber, Checkbox, Button, Divider} from 'antd';
import NfDataGrid from './../../NfDataGrid';
import resource from "./../../../Resource";
import _ from 'lodash';

class XmlEditor extends Component {

    static propTypes = {
        entity: PropTypes.object,
        cellEntity: PropTypes.object
    }

    constructor(...args) {
        super(...args);
        this.tableGrid = React.createRef();
        this.fieldGrid = React.createRef();
        this.state = {
            dontExplode: false,
            sessionId: null,
            queryResult: null,
            queryError: null
        }
    }

    runXml() {
        const {sessionId, dontExplode} = this.state
        const entity = this.props.entity
        const cellEntity = this.props.cellEntity
        resource.call({
            dontExplode: dontExplode,
            e_id: entity.e_id,
            fileName: cellEntity.path,
            nodeName: cellEntity.name,
            outputType: "json",
            rowTag: cellEntity.rowTag,
            sampleSize: cellEntity.sampleSize,
            sessionId: sessionId !== null ? sessionId : null,
            _type_: entity._type_
        }, "expandXMLFile", {}).then(json => {
            if (!json.result.valueCount) {
                this.setState({queryResult: JSON.parse(json.result), session: json.sessionId, queryError: null})
            } else {
                this.setState({queryResult: null, queryError: this.getUsableErrorView(json.result.values[0])})
            }
        })
    }

    getUsableErrorView(queryError) {
        return queryError.status + '\n' + queryError.evalue + '\n' + queryError.traceback.join('')
    }

    save() {
        const makeChildExplodeList = (key, value, explodeList) => {
            if (value && value.type === "explode") {
                let alias = (value.newname || "")
                    .replace(new RegExp("->", 'g'), "_")
                    .replace(new RegExp(":", 'g'), "_")
                let field = key.replace(new RegExp("->", 'g'), ".")
                for (let explodeNode of explodeList) {
                    field = field.replace(new RegExp("^" + explodeNode.field + "."), explodeNode.alias + ".")
                }
                explodeList.push({_type_: "etl.ExplodeField", alias, field});
            }
            if (value && value.children) {
                for (let childName of Object.keys(value.children)) {
                    const child = value.children[childName]
                    makeChildExplodeList(childName, child, explodeList)
                }
            }
        }
        const makeExplodeList = (xmlSchema, explodeList) => {
            for (let key of Object.keys(xmlSchema)) {
                const value = xmlSchema[key]
                makeChildExplodeList(key, value, explodeList)
            }
            return explodeList
        }
        const explodeFields = makeExplodeList(this.state.queryResult.xmlSchema, [])
        const newFields = this.state.queryResult.schema.fields.map(field => {
            let domainStructure = this.convertFieldType(field.type)
            let dataTypeDomain = domainStructure._type_ === "dataset.ScalarType" ? domainStructure.dataTypeDomain : null
            if (dataTypeDomain) {
                domainStructure = undefined
            }
            let xmlPath = field.name.replace(new RegExp("->", 'g'), ".")
            for (let explodeNode of explodeFields) {
                xmlPath = xmlPath.replace(new RegExp("^" + explodeNode.field + "."), explodeNode.alias + ".")
            }
            return {
                name: field.name.replace(new RegExp("->", 'g'), "_").replace(new RegExp(":", 'g'), "_"),
                xmlPath, domainStructure, dataTypeDomain,
                _type_: 'etl.XMLSourceField'
            }
        })
        const outputPort = update(this.props.cellEntity.outputPort, {$merge: {fields: newFields}})
        this.props.updateNodeEntity(update(this.props.cellEntity, {
            $merge: {
                outputPort,
                explodeFields
            }
        }), this.props.cellEntity)
    }

    findXmlPath(fieldName, xmlSchema, parent) {
        return fieldName.replace(new RegExp("->", 'g'), ".")
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
        const columns = result.schema.fields.map(fld => ({
            headerName: fld.name,
            field: fld.name,
            sortingOrder: ["asc", "desc"]
        }))
        const rowData = result.data.map(row =>
            _.mapValues(row, v => _.isObject(v) ? JSON.stringify(v) : v)
        )
        return (
            <div style={{height: '80vh', overflow: 'auto'}}>
                <div style={{boxSizing: 'border-box', height: '80vh'}} className="ag-theme-balham">
                    <NfDataGrid
                        ref={this.DataGrid}
                        columnDefs={columns}
                        rowData={rowData}
                        gridOptions={{
                            enableColResize: true,
                            pivotHeaderHeight: true,
                            enableSorting: true,
                            sortingOrder: ["desc", "asc", null],
                            enableFilter: true,
                            gridAutoHeight: true,
                            rowSelection: 'single',
                            rowMultiSelectWithClick: true,
                            onCellClicked: () => {
                                this.forceUpdate()
                            }
                        }}
                    />
                </div>
            </div>
        )
    }

    inputNumberOnChange(newValue) {
        this.props.updateNodeEntity(update(this.props.cellEntity, {$merge: {sampleSize: newValue}}), this.props.cellEntity)
    }

    render() {
        const {t, cellEntity} = this.props
        const {queryResult, queryError} = this.state
        return (
            <div style={{height: 'calc(100vh - 149px)'}}>
                <Form layout={"inline"}>
                    {queryResult && <Form.Item wrapperCol={{span: 2, push: 14}}>
                        <Tooltip placement="top" title={t("save")}>
                            <Button id="save" shape="circle" style={{border: 0}} onClick={() => {
                                this.save()
                            }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/save-modern.svg"}/>
                            </Button>
                        </Tooltip>
                    </Form.Item>}
                    <Form.Item wrapperCol={{span: 2, push: 14}}>
                        <Tooltip placement="top" title={t("show")}>
                            <Button id="show" shape="circle" style={{border: 0}} onClick={() => {
                                this.runXml()
                            }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/show-modern.svg"}/>
                            </Button>
                        </Tooltip>
                    </Form.Item>
                    <Form.Item wrapperCol={{span: 2, push: 8}}>
                        <Tooltip placement="bottom"
                                 title={t(cellEntity._type_ + '.attrs.sampleSize.caption', {ns: 'classes'})}>
                            <InputNumber size={"small"} value={this.props.cellEntity.sampleSize}
                                         onChange={(value) => this.inputNumberOnChange(value)}/>
                        </Tooltip>
                    </Form.Item>
                    <Form.Item wrapperCol={{span: 2, push: 14}}>
                        <Tooltip placement="bottom"
                                 title={t(cellEntity._type_ + '.attrs.donotExplode.caption', {ns: 'classes'})}>
                            <Checkbox
                                style={{'marginLeft': '22px'}}
                                checked={this.state.dontExplode}
                                onChange={value => {
                                    this.setState({dontExplode: value.target.checked})
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
                    editorProps={{$blockScrolling: Infinity}}
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

export default translate()(XmlEditor);
