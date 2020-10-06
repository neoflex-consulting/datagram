import React, { Component } from 'react';
import PropTypes from 'prop-types';
import AceEditor from 'react-ace';
import 'brace/mode/sql';
import 'brace/theme/sqlserver';
import 'brace/ext/searchbox';
import { translate } from 'react-i18next'
import update from 'immutability-helper'
import { Form, Tooltip, Avatar, Button, Row, Col, InputNumber, Divider } from 'antd';
import { AgGridReact } from 'ag-grid-react';
import 'ag-grid/dist/styles/ag-grid.css';
import 'ag-grid/dist/styles/ag-theme-balham.css';
import resource from "./../../../Resource";
import SplitterLayout from 'react-splitter-layout';
import { cupOfCoffee } from '../../../utils/consts';
import PortsTree from '../../PortsTree';
import JSONBigNumber from 'json-bignumber'

class SparkEditor extends Component {

    static propTypes = {
        entity: PropTypes.object,
        cellEntity: PropTypes.object
    }

    constructor(...args) {
        super(...args);
        this.aceEditor = React.createRef();
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
            ref={"aceEditor"}
            focus={true}
            cursorStart={1}
            mode={'sql'}
            width={''}
            height={'71vh'}
            theme={'sqlserver'}
            fontSize={15}
            debounceChangePeriod={500}
            editorProps={{ $blockScrolling: Infinity }}
            value={this.props.cellEntity.statement}
            onChange={(newValue, e) => this.editorOnChange(newValue)}
            showPrintMargin={false}
        />
    }

    editorOnChange(newValue) {
        this.props.updateNodeEntity(update(this.props.cellEntity, { $merge: { statement: newValue } }), this.props.cellEntity)
    }

    treeOnClick(event) {
        if (event.node.props.data) {
            const value = event.node.props.data
            this.refs.aceEditor.editor.insert(value)
            this.refs.aceEditor.editor.focus()
        }
    }

    executeSql() {
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
                this.setState({ queryResult: JSONBigNumber.parse(json.result), session: json.sessionId, queryError: null })
            } else {
                this.setState({ queryResult: null, queryError: this.getUsableErrorView(json.result.values[0]) })
            }
        }).catch(() => this.setState({ queryResult: null, queryError: null }))
    }

    getUsableErrorView(queryError) {
        return queryError.status + '\n' + queryError.evalue + '\n' + queryError.traceback.join('')
    }

    applySql() {
        const newFields = this.state.queryResult.schema.fields.map(fl => this.sparkToField(fl));
        const outputPort = update(this.props.cellEntity.outputPort, { $merge: { fields: newFields } })
        this.props.updateNodeEntity(update(this.props.cellEntity, { $merge: { outputPort } }), this.props.cellEntity)
    }

    isSimpleType(type){
        var res = !type.type;
        console.log(type);
        console.log("isSimple: " + res);
        return res;

    }
    sparkToField(fl){
            console.log("Spark to field");
            console.log(fl);
            if(this.isSimpleType(fl.type)){
                return ({ name: fl.name, dataTypeDomain: this.convertFieldType(fl.type), _type_: 'dataset.Field' });
            }

            var intType = this.convertFieldType(fl.type);
            var head;
            if("STRUCT" === intType){
                head = ({ name: fl.name, dataTypeDomain: this.convertFieldType(fl.type), _type_: 'dataset.Field', domainStructure: {_type_: 'dataset.StructType', internalStructure: {_type_: 'dataset.Structure', fields: []}} })
                head.domainStructure.internalStructure.fields = fl.type.fields.map(c=>this.sparkToChildField(c));
            }else{
                head = ({ name: fl.name, dataTypeDomain: this.convertFieldType(fl.type), _type_: 'dataset.Field', domainStructure: {_type_: 'dataset.ArrayType', elementType: {_type_: 'dataset.Structure', fields: []}} })
                head.domainStructure.elementType.fields = fl.type.elementType.fields.map(c=>this.sparkToChildField(c));
            }
            console.log("Returning head");
            console.log(head);
            return head;
     }

     sparkToChildField(fl){
         console.log("Spark to child field");
         console.log(fl);
         if(this.isSimpleType(fl.type)){
            return ({ name: fl.name, dataTypeDomain: this.convertFieldType(fl.type), _type_: 'dataset.Field' });
         }
         var child;
         var intType = this.convertFieldType(fl.type);
         if("STRUCT" === intType){
             child = ({ name: fl.name, dataTypeDomain: this.convertFieldType(fl.type), _type_: 'dataset.Field', domainStructure: {_type_: 'dataset.StructType', internalStructure: {_type_: 'dataset.Structure', fields: []}} })
             child.domainStructure.internalStructure.fields = fl.type.fields.map(c=>this.sparkToChildField(c));
         }else{
            child = ({ name: fl.name, dataTypeDomain: this.convertFieldType(fl.type), _type_: 'dataset.Field', domainStructure: {_type_: 'dataset.ArrayType', elementType: {_type_: 'dataset.Structure', fields: []}} })
            child.domainStructure.elementType.fields = fl.type.elementType.fields.map(c=>this.sparkToChildField(c));
         }
         return child;
     }

    convertFieldType(type) {

        if(type instanceof Object){
            return type.type.toUpperCase();
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

    render() {
        const { t } = this.props
        const { queryResult, queryError } = this.state
        return (
            <div style={{ height: 'calc(100vh - 149px)' }}>
                <SplitterLayout
                    customClassName='splitter-sql-editor'
                    percentage={true}
                    primaryIndex={0}
                    vertical={true}
                    primaryMinSize={15}
                    secondaryMinSize={15}
                    secondaryInitialSize={15}
                >
                    <div>
                        <Row >
                            <Col span={20}>
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
                                        <Tooltip placement="top" title={t(this.props.cellEntity._type_ + '.attrs.sampleSize.caption', { ns: 'classes' })}>
                                            <InputNumber size="small" value={this.props.cellEntity.sampleSize} onChange={(value) => this.inputNumberOnChange(value)} />
                                        </Tooltip>
                                    </Form.Item>
                                </Form>
                                <Divider style={{marginTop: 0, marginBottom: 0}}/>
                                {this.createEditor()}
                            </Col>
                            <Col span={4}>
                                <div style={{ height: 'calc(100vh - 280px)', overflow: 'auto' }}>
                                    <PortsTree ports={this.props.cellEntity.sqlPorts} treeOnClick={(event) => this.treeOnClick(event)} />
                                </div>
                            </Col>
                        </Row>
                    </div>
                    <div>
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
                        />
                            : queryResult && this.createDisplayList()}
                    </div>
                </SplitterLayout>

            </div>
        )
    }

}

export default translate()(SparkEditor);
