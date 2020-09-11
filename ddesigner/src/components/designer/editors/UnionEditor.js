import React, {Component} from 'react';
import PropTypes from 'prop-types';
import {getClassDef} from '../../../model.js';
import 'brace/mode/scala';
import 'brace/mode/sql';
import 'brace/theme/sqlserver';
import 'brace/ext/searchbox';
import {translate} from 'react-i18next'
import update from 'immutability-helper'
import {Tooltip, Avatar, Menu, Row, Col, Table, Input, Form, Select} from 'antd';
import 'ag-grid/dist/styles/ag-grid.css';
import 'ag-grid/dist/styles/ag-theme-fresh.css';
import _ from 'lodash';

const FormItem = Form.Item
const MenuItem = Menu.Item
const Option = Select.Option
const formItemLayout = {
    labelCol: {span: 4},
    wrapperCol: {span: 14},
    style: {marginBottom: "12px"}
}

class UnionEditor extends Component {

    static propTypes = {
        entity: PropTypes.object,
        cellEntity: PropTypes.object
    }

    constructor(...args) {
        super(...args);
        this.state = {
            selectedRowKeys: [0]
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
        this.props.updateNodeEntity(update(this.props.cellEntity, {outputPort: {fields: {[this.getIndex()]: {$merge: f}}}}), this.props.cellEntity)
    }

    getInputFields() {
        return _.sortBy(this.props.cellEntity.inputPort.fields.map(f => {return {...f, key: f.name, original: f}}), (f) => f.key.toLowerCase())
    }

    getUnionFields() {
        return _.sortBy(this.props.cellEntity.unionPort.fields.map(f => {return {...f, key: f.name, original: f}}), (f) => f.key.toLowerCase())
    }

    getSelectedInputFields() {
        const selectedField = this.getSelectedField().inputPortField
        return selectedField ? this.getInputFields().filter(i=>(selectedField === i.original) || (i.e_id && i.e_id === selectedField.e_id)) : []
    }

    getSelectedUnionFields() {
        const selectedField = this.getSelectedField().unionPortField
        return selectedField ? this.getUnionFields().filter(i=>(selectedField === i.original) || (i.e_id && i.e_id === selectedField.e_id)) : []
    }

    render() {
        const {t} = this.props
        const classDef = getClassDef("etl.UnionField")
        const dataTypeDomainField = classDef.fields.find(f => f.name === "dataTypeDomain")
        return (
            <Row>
                <Col span={6}>
                    <Menu mode="horizontal" selectable={false} onClick={(val) => {
                        if (val.key === "new") {
                            const index = this.props.cellEntity.outputPort.fields.length
                            const f = {
                                _type_: 'etl.UnionField',
                                name: this.getNewFieldName(),
                                sourceFields: [],
                                dataTypeDomain: "STRING"
                            }
                            this.props.updateNodeEntity(update(this.props.cellEntity, {outputPort: {fields: {$push: [f]}}}), this.props.cellEntity)
                            this.setState({selectedRowKeys: [index]})
                        }
                        else if (val.key === "delete") {
                            const index = this.getIndex()
                            if (index >= 0) {
                                const list = this.props.cellEntity.outputPort.fields
                                const fields = [...list.slice(0, index), ...list.slice(index + 1)]
                                this.props.updateNodeEntity(update(this.props.cellEntity, {outputPort: {$merge: {fields}}}), this.props.cellEntity)
                                this.setState({selectedRowKeys: [index < list.length - 1 ? index : list.length - 2]})
                            }
                        }
                        else if (val.key === "up") {
                            const index = this.getIndex()
                            if (index > 0) {
                                const list = this.props.cellEntity.outputPort.fields
                                const fields = [...list.slice(0, index - 1), list[index], list[index - 1], ...list.slice(index + 1)]
                                this.props.updateNodeEntity(update(this.props.cellEntity, {outputPort: {$merge: {fields}}}), this.props.cellEntity)
                                this.setState({selectedRowKeys: [index - 1]})
                            }
                        }
                        else if (val.key === "down") {
                            const index = this.getIndex()
                            const list = this.props.cellEntity.outputPort.fields
                            if (index >= 0 && index < list.length - 1) {
                                const fields = [...list.slice(0, index), list[index + 1], list[index], ...list.slice(index + 2)]
                                this.props.updateNodeEntity(update(this.props.cellEntity, {outputPort: {$merge: {fields}}}), this.props.cellEntity)
                                this.setState({selectedRowKeys: [index + 1]})
                            }
                        }
                    }}>
                        <MenuItem key={"new"}>
                            <Tooltip title={t("new")}>
                                <Avatar className="avatar-button-tool-panel" src={"images/icon-core/plus-modern.svg"}/>
                            </Tooltip>
                        </MenuItem>
                        <MenuItem key={"up"}>
                            <Tooltip title={t("up")}>
                                <Avatar className="avatar-button-tool-panel"
                                        src={"images/icon-core/arrow-up-modern.svg"}/>
                            </Tooltip>
                        </MenuItem>
                        <MenuItem key={"down"}>
                            <Tooltip title={t("down")}>
                                <Avatar className="avatar-button-tool-panel"
                                        src={"images/icon-core/arrow-down-modern.svg"}/>
                            </Tooltip>
                        </MenuItem>
                        <MenuItem key={"delete"}>
                            <Tooltip title={t("delete")}>
                                <Avatar className="avatar-button-tool-panel"
                                        src={"images/icon-core/delete-modern.svg"}/>
                            </Tooltip>
                        </MenuItem>
                    </Menu>
                    <Table
                        dataSource={this.props.cellEntity.outputPort.fields.map((f, index) => {
                            return {key: index, name: f.name, dataTypeDomain: f.dataTypeDomain}
                        })}
                        columns={[
                            {
                                title: "Name",
                                dataIndex: "name",
                                render: (text, record) => {
                                    return <Input value={record.name} size={"small"} readOnly={true}/>
                                }
                            }
                        ]}
                        rowSelection={{
                            type: "radio",
                            selectedRowKeys: this.state.selectedRowKeys,
                            onChange: (selectedRowKeys, selectedRows) => {
                                this.setState({selectedRowKeys, validationResult: undefined, activeKey: "expression"})
                            }
                        }}
                        size={"small"}
                        pagination={false}
                        scroll={{y: 450}}
                        showHeader={false}
                    />
                </Col>
                {this.getSelectedField() &&
                <Col span={18}>
                    <Form onSubmit={e => e.preventDefault()} layout={"horizontal"}>
                        <FormItem label={t('etl.UnionField.attrs.name.caption', {ns: 'classes'})} {...formItemLayout}>
                            <Input value={this.getSelectedField().name} size={"small"} onChange={e => {
                                this.updateOutputField({name: e.target.value})
                            }}/>
                        </FormItem>
                        <FormItem label={t('etl.UnionField.attrs.dataTypeDomain.caption', {ns: 'classes'})} {...formItemLayout}>
                            <Select value={this.getSelectedField().dataTypeDomain} size={"small"} onChange={value => {
                                this.updateOutputField({dataTypeDomain: value})
                            }}>
                                {dataTypeDomainField.options.map(opt => <Option key={opt} value={opt}>{opt}</Option>)}
                            </Select>
                        </FormItem>
                        <FormItem label={t('etl.UnionField.attrs.inputPortField.caption', {ns: 'classes'})} {...formItemLayout}>
                            <Select className="ant-select-no-padding"
                                    showSearch
                                    allowClear
                                    size={"small"}
                                    value={this.getSelectedInputFields().map(f => f.key)[0]}
                                    onChange={value => {
                                        this.updateOutputField({inputPortField: this.getInputFields().filter(f => value === f.key).map(f => f.original)[0]})
                                    }}
                                    filterOption={(input, option) => option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0}
                            >
                                {this.getInputFields().map(ent =>
                                    <Option key={ent.key}
                                            value={ent.key}>{ent.key + ": " + ent.dataTypeDomain}</Option>)}
                            </Select>
                        </FormItem>
                        <FormItem label={t('etl.UnionField.attrs.unionPortField.caption', {ns: 'classes'})} {...formItemLayout}>
                            <Select className="ant-select-no-padding"
                                    showSearch
                                    allowClear
                                    size={"small"}
                                    value={this.getSelectedUnionFields().map(f => f.key)[0]}
                                    onChange={value => {
                                        this.updateOutputField({unionPortField: this.getUnionFields().filter(f => value === f.key).map(f => f.original)[0]})
                                    }}
                                    filterOption={(input, option) => option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0}
                            >
                                {this.getUnionFields().map(ent =>
                                    <Option key={ent.key}
                                            value={ent.key}>{ent.key + ": " + ent.dataTypeDomain}</Option>)}
                            </Select>
                        </FormItem>
                    </Form>
                </Col>}
            </Row>
        )
    }

}

export default translate()(UnionEditor);
