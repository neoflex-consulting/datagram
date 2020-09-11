import React, {Component} from 'react'
import {translate} from 'react-i18next'
import {Avatar, Button, Col, Collapse, Form, Input, Row, Select, Tooltip, Tabs} from 'antd'
import 'ag-grid/dist/styles/ag-grid.css'
import 'ag-grid/dist/styles/ag-theme-balham.css'
import _ from 'lodash'
import update from 'immutability-helper'
import Debounced from '../Debounced'
import StructGrid from './structure/StructGrid'
import resource from '../../Resource'
import HiveDsMetadata from './metadata/HiveDsMetadata'
import HiveExternalMetadata from './metadata/HiveExternalMetadata'
import TableMetadata from './metadata/TableMetadata'
import QueryMetadata from './metadata/QueryMetadata'
import ReferenceMetadata from './metadata/ReferenceMetadata'
import LinkedMetadata from './metadata/LinkedMetadata'

const FormItem = Form.Item
const {TextArea} = Input
const Panel = Collapse.Panel
const Option = Select.Option

// TODO Update columns + Merge data while updateDsMetdatda instead of overwrite

const specificMetdata = {
    "sse.HiveDataset": HiveDsMetadata,
    "sse.HiveExternalDataset": HiveExternalMetadata,
    "sse.TableDataset": TableMetadata,
    "sse.QueryDataset": QueryMetadata,
    "sse.ReferenceDataset": ReferenceMetadata,
    "sse.LinkedDataset": LinkedMetadata,
}

class Metadata extends Component {
    constructor(props) {
        super(props)
        this.state = {selects: {}}
    }

    componentDidMount() {
        resource.getSimpleSelect('sse.Workspace', ['name']).then(list => {
            this.setState(update(this.state, {selects: {'workspace': {$set: list}}}))
        })
    }

    getFieldLabel(name) {
        const {t, entity} = this.props
        return t(entity._type_ + '.attrs.' + name + '.caption', {ns: 'classes'})
    }

    renderTextField(value, disabled = false) {
        const {entity, updateEntity} = this.props
        return <Debounced Component={TextArea} autosize={{minRows: 2, maxRows: 40}} id={value} disabled={disabled}
                          value={entity[value]} onChange={e =>
            updateEntity({[value]: e.target.value})
        }/>
    }

    renderStringField(value, disabled = false, nestedValue = null) {
        const {entity, updateEntity} = this.props
        return <Debounced
            id={value} value={_.get(entity[value], nestedValue, entity[value])}
            disabled={disabled}
            onChange={e =>
                nestedValue ?
                    updateEntity({[value.nestedValue]: e.target.value})
                    :
                    updateEntity({[value]: e.target.value})
            }/>
    }

    renderSelectField(field) {
        const {entity, updateEntity, t} = this.props
        const editButtonWidth = _.get(entity, [field, 'e_id']) ? 36 : 0
        const displayField = "name"
        const selects = _.get(this.state.selects, field, [])
        return <Input.Group compact>
            <Select className="ant-select-no-padding" disabled={false} showSearch
                    style={{width: `calc(100% - ${editButtonWidth}pt)`}}
                    allowClear
                    id={field} value={_.get(entity, [field, 'e_id'])}
                    onChange={value => {
                        updateEntity({[field]: selects.find(ent => ent.e_id === value)})
                    }}
                    filterOption={(input, option) => option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0}
            >
                {_.sortBy(selects, (o) => _.get(o, displayField, '').toLowerCase()).map(ent =>
                    <Option key={ent.e_id} value={ent.e_id}>{_.get(ent, displayField)}</Option>
                )}
            </Select>
            {editButtonWidth > 0 &&
            <Tooltip placement="top" title={t("edit")}>
                <Button type="dashed" placement="" onClick={() => {
                    const {_type_, e_id, name} = _.get(entity, [field])
                    this.props.selectObject({_type_, e_id, name})
                }}><Avatar className='avatar-button-property' size='small'
                           src='images/icon-core/edit-modern.svg'/></Button>
            </Tooltip>}
        </Input.Group>
    }

    render() {
        const {t, entity, updateEntity} = this.props
        const SpecificData = specificMetdata[entity._type_]

        return (
            <Tabs defaultActiveKey="common">
                <Tabs.TabPane tab="Common" key="common">
                    <Row gutter={28}>
                        <Col span={1}/>
                        <Col span={10}>
                            <Form onSubmit={e => e.preventDefault()} layout={"vertical"}>
                                <FormItem label={this.getFieldLabel('workspace')}>
                                    {this.renderSelectField('workspace')}
                                </FormItem>
                                <FormItem label={this.getFieldLabel('shortName')}>
                                    <Debounced
                                        id={'shortName'} value={entity['shortName']}
                                        onChange={e =>
                                            updateEntity({
                                                'shortName': e.target.value,
                                                'name': entity.workspace.name + "_" + e.target.value
                                            })
                                        }/>
                                </FormItem>
                                <FormItem label={this.getFieldLabel('description')}>
                                    {this.renderTextField('description')}
                                </FormItem>
                                <FormItem label={this.getFieldLabel('partitionByCols')}>
                                    <Select className="ant-select-no-padding"
                                            showSearch
                                            allowClear
                                            mode={"multiple"}
                                            id={"partitionByCols"}
                                            value={(entity['partitionByCols'] || [])}
                                            onChange={newValues => {
                                                updateEntity({partitionByCols: newValues})
                                            }}
                                    >
                                        {(entity['columns'] || []).map(ent =>
                                            <Option key={ent.columnName} value={ent.columnName}>{ent.columnName}</Option>
                                        )}
                                    </Select>
                                </FormItem>
                                <FormItem>
                                    <Collapse>
                                        <Panel header={this.getFieldLabel('permissions')}>
                                            <FormItem label={this.getFieldLabel('owner')}>
                                                {this.renderStringField('owner')}
                                            </FormItem>
                                            <FormItem label={this.getFieldLabel('group')}>
                                                {this.renderStringField('group')}
                                            </FormItem>
                                            <FormItem label={this.getFieldLabel('permissions')}>
                                                {this.renderStringField('permissions')}
                                            </FormItem>
                                        </Panel>
                                    </Collapse>
                                </FormItem>
                                <FormItem>
                                    <Collapse>
                                        <Panel header={t('auth.AuditInfo.caption', {ns: 'classes'})}>
                                            <FormItem label={t('auth.AuditInfo.attrs.createUser.caption', {ns: 'classes'})}>
                                                {this.renderStringField('auditInfo', true, 'createUser')}
                                            </FormItem>
                                            <FormItem label={t('auth.AuditInfo.attrs.createDateTime.caption', {ns: 'classes'})}>
                                                {this.renderStringField('auditInfo', true, 'createDateTime')}
                                            </FormItem>
                                            <FormItem label={t('auth.AuditInfo.attrs.changeUser.caption', {ns: 'classes'})}>
                                                {this.renderStringField('auditInfo', true, 'changeUser')}
                                            </FormItem>
                                            <FormItem label={t('auth.AuditInfo.attrs.changeDateTime.caption', {ns: 'classes'})}>
                                                {this.renderStringField('auditInfo', true, 'changeDateTime')}
                                            </FormItem>
                                        </Panel>
                                    </Collapse>
                                </FormItem>
                            </Form>
                        </Col>
                        <Col span={1}>
                            <div style={{height: 'calc(100vh - 60px)', borderRight: '2px solid #fafafa'}}/>
                        </Col>
                        <Col span={11}>
                            <Form onSubmit={e => e.preventDefault()} layout={"vertical"}>
                                {SpecificData && <SpecificData {...this.props}/>}
                            </Form>
                        </Col>
                        <Col span={1}/>
                    </Row>
                </Tabs.TabPane>
                <Tabs.TabPane tab={this.getFieldLabel('columns')} key={this.getFieldLabel('columns')}>
                    <div style={{marginTop: '7px', height: 'calc(100vh - 100px)'}}>
                        <StructGrid {...this.props}/>
                    </div>
                </Tabs.TabPane>
            </Tabs>
        )
    }
}

export default translate()(Metadata);
