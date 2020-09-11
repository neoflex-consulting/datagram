import React, {Component} from 'react';
import {translate} from 'react-i18next'
import {Form, Input, Select, Tooltip, Button, Avatar} from 'antd'
import _ from 'lodash'
import update from 'immutability-helper'
import Debounced from '../../Debounced'
import resource from '../../../Resource'

const FormItem = Form.Item
const Option = Select.Option

class TableMetadata extends Component {

    constructor(props) {
        super(props)
        this.state = {selects: {}}
    }

    componentDidMount() {
        resource.getSimpleSelect('rt.JdbcConnection', ['name']).then(list => {
            this.setState(update(this.state, {selects: {'connection': {$set: list}}}))
        })
    }

    // FIXME 1. Copy/paste from Metadata (rewrite to static util methods with parameters)
    // FIXME 2. doesn't display anyway
    getFieldLabel(name) {
        const {t, entity} = this.props
        return t(entity._type_ + '.attrs.' + name + '.caption', {ns: 'classes'})
    }

    renderStringField(value, disabled = false, nestedValue = null) {
        const {entity, updateEntity} = this.props
        const val = _.get(entity[value], nestedValue, entity[value])
        if (value == null) {
            return null
        }
        return <Debounced
            id={value} value={val}
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
        return [
            <FormItem label={this.getFieldLabel('connection')} key={this.getFieldLabel('connection')}>
                {this.renderSelectField('connection')}
            </FormItem>,
            <FormItem label={this.getFieldLabel('schema')} key={this.getFieldLabel('schema')}>
                {this.renderStringField('schema')}
            </FormItem>,
            <FormItem label={this.getFieldLabel('tableName')} key={this.getFieldLabel('tableName')}>
                {this.renderStringField('tableName')}
            </FormItem>]
    }
}

export default translate()(TableMetadata);