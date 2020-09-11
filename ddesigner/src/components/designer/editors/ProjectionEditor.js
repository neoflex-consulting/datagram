import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { getClassDef, fullJavaClassName } from '../../../model.js';
import AceEditor from 'react-ace';
import 'brace/mode/scala';
import 'brace/mode/sql';
import 'brace/theme/tomorrow';
import 'brace/ext/searchbox';
import { translate } from 'react-i18next'
import update from 'immutability-helper'
import { Tooltip, Avatar, Menu, Row, Col, Table, Input, Form, Select, Tabs, Tree, Icon } from 'antd';
import 'ag-grid/dist/styles/ag-grid.css';
import 'ag-grid/dist/styles/ag-theme-fresh.css';
import resource from "./../../../Resource";
import _ from 'lodash';

const TreeNode = Tree.TreeNode
const FormItem = Form.Item
const MenuItem = Menu.Item
const Option = Select.Option
const { TabPane } = Tabs
const { TextArea } = Input
const formItemLayout = {
    labelCol: { span: 4 },
    wrapperCol: { span: 14 },
    style: { marginBottom: "12px" }
}

class ProjectionEditor extends Component {

    static propTypes = {
        entity: PropTypes.object,
        cellEntity: PropTypes.object
    }

    constructor(...args) {
        super(...args);
        this.aceEditor = React.createRef();
        this.state = {
            selectedRowKeys: [0],
            activeKey: "expression",
            selectedSourceFileds: []
        }
    }

    validate() {
        const field = this.getSelectedField()
        if (field) {
            const fieldOperationType = field.fieldOperationType
            const action = fieldOperationType === "TRANSFORM" ? "test" : (fieldOperationType === "SQL" ? "validateSQLField" : undefined)
            if (action) {
                this.setState({ validationResult: undefined, activeKey: "expression" })
                const parent = this.props.entity
                const parentType = this.props.cellEntity._type_ === 'etl.Projection' ? "devs.transformation.Projection" : "devs.transformation.Join"
                const sourceFields = field.sourceFields.map(f => {
                    const sourceType = this.props.cellEntity.inputPort.fields.includes(f) ? "in" : "join"
                    const javaDomain = fullJavaClassName[f.dataTypeDomain]
                    return { ...f, sourceType, javaDomain }
                })
                const argField = update(field, { $merge: { parent, parentType, sourceFields } })
                resource.call(argField, action, {}).then(validationResult => {
                    let message = validationResult.message ? validationResult.message : (validationResult.result.values ? JSON.stringify(validationResult.result.values, null, ' ') : null)
                    if (typeof message === "object") {
                        message = JSON.stringify(message, null, ' ')
                    }
                    let result = validationResult.result === true
                    this.setState({
                        validationResult: { result, message },
                        activeKey: result ? "expression" : "validation"
                    })
                })
            }
        }

    }

    getNewFieldName() {
        let i = 0
        while (true) {
            const name = `Option${i}`
            if (!this.props.cellEntity.outputPort.fields.map(f => f.name).includes(name)) {
                return name
            }
            ++i
        }
    }

    getIndex() {
        if (this.state.selectedRowKeys.length <= 0) {
            return -1
        }
        return this.state.selectedRowKeys[0]
    }

    getSelectedField() {
        return this.props.cellEntity.outputPort.fields[this.getIndex()]
    }

    updateOutputField(f) {
        this.props.updateNodeEntity(update(this.props.cellEntity, { outputPort: { fields: { [this.getIndex()]: { $merge: f } } } }), this.props.cellEntity)
    }

    getEditorType() {
        const fieldOperationType = this.getSelectedField().fieldOperationType
        if (fieldOperationType === "TRANSFORM") {
            return "scala"
        }
        if (fieldOperationType === "SQL") {
            return "sql"
        }
        return undefined
    }

    getInFields() {
        const hasJoin = !!this.props.cellEntity.joineePort
        const inputFields = this.props.cellEntity.inputPort.fields.map(f => {
            return { ...f, key: `${hasJoin ? "_1." : ""}${f.name}`, original: f }
        })
        const joineeFields = !hasJoin ? [] : this.props.cellEntity.joineePort.fields.map(f => {
            return { ...f, key: `${hasJoin ? "_2." : ""}${f.name}`, original: f }
        })
        return _.sortBy([...inputFields, ...joineeFields], (f) => f.key.toLowerCase())
    }

    getSelectedSources() {
        const inFields = this.getInFields()
        return this.getSelectedField().sourceFields.map(s => {
            return inFields.find(i => (s === i.original) || (i.e_id && i.e_id === s.e_id))
        }).filter(f => !!f)
    }

    treeOnClick(event) {
        if (event.node.props.data) {
            let value = event.node.props.title.split(':')[0]
            this.refs.aceEditor.editor.insert(value)
            this.refs.aceEditor.editor.focus()
        }
    }

    createTreePane() {
        const { cellEntity } = this.props
        const selectedSourceFileds = this.state.selectedSourceFileds.length > 0 ? this.state.selectedSourceFileds : this.getSelectedSources().map(f => f.key)
        const hasJoin = !!this.props.cellEntity.joineePort
        const inputFields = this.props.cellEntity.inputPort.fields.map(f => {
            return { ...f, key: `${hasJoin ? "_1." : ""}${f.name}`, original: f }
        })
        const joineeFields = !hasJoin ? [] : this.props.cellEntity.joineePort.fields.map(f => {
            return { ...f, key: `${hasJoin ? "_2." : ""}${f.name}`, original: f }
        })
        const concatInputAndJoinee = _.concat(inputFields, joineeFields)
        return (cellEntity && <Tree
            showLine
            onSelect={
                (key, event) => this.treeOnClick(event)
            }
        >
            {!hasJoin ? cellEntity.inputPort.fields.filter(field => selectedSourceFileds.find((f) => f === field.name)).map(field => <TreeNode title={field.name + ': ' + field.dataTypeDomain} key={field.key} data={field.name} />) : []}

            {hasJoin ? <div>
                {concatInputAndJoinee.filter(field => selectedSourceFileds.find((f) => f === field.key)).map(field => <TreeNode title={field.key + ': ' + field.dataTypeDomain} key={field.key} data={field.name} />)}
            </div> : []}
        </Tree>)
    }

    render() {
        const { t } = this.props
        const classDef = getClassDef("etl.ProjectionField")
        const fieldOperationTypeField = classDef.fields.find(f => f.name === "fieldOperationType")
        const dataTypeDomainField = classDef.fields.find(f => f.name === "dataTypeDomain")
        return (
            <Row>
                <Col span={6}>
                    <Menu mode="horizontal" selectable={false} onClick={(val) => {
                        if (val.key === "new") {
                            const index = this.props.cellEntity.outputPort.fields.length
                            const f = {
                                _type_: 'etl.ProjectionField',
                                name: this.getNewFieldName(),
                                fieldOperationType: "SQL",
                                sourceFields: [],
                                dataTypeDomain: "STRING",
                                expression: '""'
                            }
                            this.props.updateNodeEntity(update(this.props.cellEntity, { outputPort: { fields: { $push: [f] } } }), this.props.cellEntity)
                            this.setState({ selectedRowKeys: [index] })
                        }
                        else if (val.key === "paste") {
                            navigator.clipboard.readText()
                              .then(text => {
                                  try {
                                      var copiedFields = JSON.parse(text)
                                      if(Array.isArray(copiedFields)) {
                                          copiedFields.map(f=>{
                                              f._type_ = 'etl.ProjectionField'
                                              f.sourceFields = f.sourceFields || []
                                              f.fieldOperationType = f.fieldOperationType || "SQL"
                                              f.expression = f.expression || '""'
                                              return f
                                          })
                                          this.props.updateNodeEntity(update(this.props.cellEntity, { outputPort: { fields: copiedFields } }), this.props.cellEntity)
                                      }
                                  } finally {

                                  }
                              })
                        }
                        else if (val.key === "delete") {
                            const index = this.getIndex()
                            if (index >= 0) {
                                const list = this.props.cellEntity.outputPort.fields
                                const fields = [...list.slice(0, index), ...list.slice(index + 1)]
                                this.props.updateNodeEntity(update(this.props.cellEntity, { outputPort: { $merge: { fields } } }), this.props.cellEntity)
                                this.setState({ selectedRowKeys: [index < list.length - 1 ? index : list.length - 2] })
                            }
                        }
                        else if (val.key === "up") {
                            const index = this.getIndex()
                            if (index > 0) {
                                const list = this.props.cellEntity.outputPort.fields
                                const fields = [...list.slice(0, index - 1), list[index], list[index - 1], ...list.slice(index + 1)]
                                this.props.updateNodeEntity(update(this.props.cellEntity, { outputPort: { $merge: { fields } } }), this.props.cellEntity)
                                this.setState({ selectedRowKeys: [index - 1] })
                            }
                        }
                        else if (val.key === "down") {
                            const index = this.getIndex()
                            const list = this.props.cellEntity.outputPort.fields
                            if (index >= 0 && index < list.length - 1) {
                                const fields = [...list.slice(0, index), list[index + 1], list[index], ...list.slice(index + 2)]
                                this.props.updateNodeEntity(update(this.props.cellEntity, { outputPort: { $merge: { fields } } }), this.props.cellEntity)
                                this.setState({ selectedRowKeys: [index + 1] })
                            }
                        }
                    }}>
                        <MenuItem key={"new"}>
                            <Tooltip title={t("new")}>
                                <Avatar className="avatar-button-tool-panel" src={"images/icon-core/plus-modern.svg"} />
                            </Tooltip>
                        </MenuItem>
                        <MenuItem key={"paste"}>
                            <Tooltip title={t("paste")}>
                                <Icon type="export"/>
                            </Tooltip>
                        </MenuItem>
                        <MenuItem key={"up"}>
                            <Tooltip title={t("up")}>
                                <Avatar className="avatar-button-tool-panel"
                                    src={"images/icon-core/arrow-up-modern.svg"} />
                            </Tooltip>
                        </MenuItem>
                        <MenuItem key={"down"}>
                            <Tooltip title={t("down")}>
                                <Avatar className="avatar-button-tool-panel"
                                    src={"images/icon-core/arrow-down-modern.svg"} />
                            </Tooltip>
                        </MenuItem>
                        <MenuItem key={"delete"}>
                            <Tooltip title={t("delete")}>
                                <Avatar className="avatar-button-tool-panel"
                                    src={"images/icon-core/delete-modern.svg"} />
                            </Tooltip>
                        </MenuItem>
                    </Menu>
                    <Table
                        dataSource={this.props.cellEntity.outputPort.fields.map((f, index) => {
                            return { key: index, name: f.name, dataTypeDomain: f.dataTypeDomain }
                        })}
                        columns={[
                            {
                                title: "Name",
                                dataIndex: "name",
                                render: (text, record) => {
                                    return <Input value={record.name} size={"small"} readOnly={true} />
                                }
                            }
                        ]}
                        rowSelection={{
                            type: "radio",
                            selectedRowKeys: this.state.selectedRowKeys,
                            onChange: (selectedRowKeys, selectedRows) => {
                                this.setState({ selectedRowKeys, validationResult: undefined, activeKey: "expression" })
                            }
                        }}
                        size={"small"}
                        pagination={false}
                        scroll={{ y: 450 }}
                        showHeader={false}
                    />
                </Col>
                {this.getSelectedField() &&
                    <Col span={18}>
                        <Form onSubmit={e => e.preventDefault()} layout={"horizontal"}>
                            <FormItem label={t('etl.ProjectionField.attrs.name.caption', { ns: 'classes' })} {...formItemLayout}>
                                <Input value={this.getSelectedField().name} size={"small"} onChange={e => {
                                    this.updateOutputField({ name: e.target.value })
                                }} />
                            </FormItem>
                            <FormItem label={t('etl.ProjectionField.attrs.fieldOperationType.caption', { ns: 'classes' })} {...formItemLayout}>
                                <Select value={this.getSelectedField().fieldOperationType} size={"small"}
                                    onChange={value => {
                                        this.updateOutputField({ fieldOperationType: value })
                                    }}>
                                    {fieldOperationTypeField.options.map(opt => <Option key={opt}
                                        value={opt}>{opt}</Option>)}
                                </Select>
                            </FormItem>
                            <FormItem label={t('etl.ProjectionField.attrs.dataTypeDomain.caption', { ns: 'classes' })} {...formItemLayout}>
                                <Select value={this.getSelectedField().dataTypeDomain} size={"small"} onChange={value => {
                                    this.updateOutputField({ dataTypeDomain: value })
                                }}>
                                    {dataTypeDomainField.options.map(opt => <Option key={opt} value={opt}>{opt}</Option>)}
                                </Select>
                            </FormItem>
                            {this.getSelectedField().fieldOperationType === "ADD" &&
                                <FormItem label={t('etl.ProjectionField.attrs.sourceFields.caption', { ns: 'classes' })} {...formItemLayout}>
                                    <Select className="ant-select-no-padding"
                                        showSearch
                                        size={"small"}
                                        value={this.getSelectedSources().map(f => f.key)}
                                        onChange={value => {
                                            this.updateOutputField({ sourceFields: [value].map(key => this.getInFields().find(f => key === f.key)).map(f => f.original) })
                                            this.setState({ selectedSourceFileds: [value] })
                                        }}
                                        filterOption={(input, option) => option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0}
                                    >
                                        {this.getInFields().map(ent =>
                                            <Option key={ent.key}
                                                value={ent.key}>{ent.key + ": " + ent.dataTypeDomain}</Option>)}
                                    </Select>
                                </FormItem>}
                            {this.getSelectedField().fieldOperationType !== "ADD" &&
                                <FormItem label={t('etl.ProjectionField.attrs.sourceFields.caption', { ns: 'classes' })} {...formItemLayout}>
                                    <Select className="ant-select-no-padding"
                                        showSearch
                                        allowClear
                                        size={"small"}
                                        mode="multiple"
                                        value={this.getSelectedSources().map(f => f.key)}
                                        onChange={values => {
                                            this.updateOutputField({ sourceFields: values.map(key => this.getInFields().find(f => key === f.key)).map(f => f.original) })
                                            this.setState({ selectedSourceFileds: values })
                                        }}
                                        filterOption={(input, option) => option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0}
                                    >
                                        {this.getInFields().map(ent =>
                                            <Option key={ent.key}
                                                value={ent.key}>{ent.key + ": " + ent.dataTypeDomain}</Option>)}
                                    </Select>
                                </FormItem>}
                        </Form>
                        {this.getEditorType() &&
                            <Row>
                                <Menu mode="horizontal" selectable={false} onClick={(val) => {
                                    if (val.key === "check") {
                                        this.validate()
                                    }
                                }}>
                                    <MenuItem key={"check"}>
                                        <Tooltip title={t("check")}>
                                            <Avatar className="avatar-button-tool-panel"
                                                src={"images/icon-core/check-modern.svg"} />
                                        </Tooltip>
                                    </MenuItem>
                                </Menu>
                                <Row>
                                    <Col span={24}>
                                        <Tabs activeKey={this.state.activeKey}
                                            onChange={(activeKey) => this.setState({ activeKey })}>
                                            <TabPane key={"expression"} tab={t('etl.ProjectionField.attrs.expression.caption', { ns: 'classes' })}>
                                                <Row>
                                                    <Col span={20}>
                                                        <AceEditor
                                                            ref={"aceEditor"}
                                                            animated={false}
                                                            mode={this.getEditorType()}
                                                            width={"100vh"}
                                                            height={"55.5vh"}
                                                            onChange={(code) => {
                                                                this.updateOutputField({ expression: code })
                                                            }}
                                                            name={"test"}
                                                            editorProps={{}}
                                                            value={this.getSelectedField().expression}
                                                            theme={"tomorrow"}
                                                            debounceChangePeriod={500}
                                                        />
                                                    </Col>
                                                    <div style={{ height: '55.5vh', overflow: 'auto' }}>
                                                        <Col span={4}>
                                                            {this.createTreePane()}
                                                        </Col>
                                                    </div>
                                                </Row>
                                            </TabPane>
                                            {this.state.validationResult &&
                                                <TabPane key={"validation"}
                                                    tab={<span
                                                        style={{ color: this.state.validationResult.result ? 'green' : 'red' }}>{this.state.validationResult.result ? "OK" : "Error"}</span>}>
                                                    <TextArea style={{ fontFamily: "monospace" }}
                                                        autosize={{ minRows: 10, maxRows: 10 }} readOnly={true}
                                                        value={this.state.validationResult.message} />
                                                </TabPane>}
                                        </Tabs>
                                    </Col >
                                </Row>
                            </Row>
                        }
                    </Col>}
            </Row>
        )
    }

}

export default translate()(ProjectionEditor);
