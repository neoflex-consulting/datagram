import React, {Component} from 'react';
import {translate} from 'react-i18next'
import {Form, Select} from 'antd'
import _ from 'lodash'
import Debounced from '../../Debounced'

const FormItem = Form.Item
const Option = Select.Option

class ReferenceMetadata extends Component {

    constructor(props) {
        super(props)
        this.state = {selects: {}}
    }

    componentDidMount() {
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

    render() {
        const {entity, updateEntity} = this.props
        return (
            <FormItem label={this.getFieldLabel('primaryKeyCols')} key={this.getFieldLabel('primaryKeyCols')}>
                <Select className="ant-select-no-padding" showSearch
                        allowClear
                        mode="multiple"
                        id={"primaryKeyCols"}
                        value={_.get(entity, "primaryKeyCols")}
                        onChange={value => {
                            updateEntity({"primaryKeyCols": value})
                        }}
                        filterOption={(input, option) => option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0}
                >
                    {_.sortBy(_.get(entity, "columns"), (o) => _.get(o, "columnName", '').toLowerCase()).map(ent =>
                        <Option key={ent.e_id} value={_.get(ent, "columnName")}>{_.get(ent, "columnName")}</Option>
                    )}
                </Select>
            </FormItem>
        )
    }
}

export default translate()(ReferenceMetadata);