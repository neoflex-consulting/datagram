import React, {Component} from 'react';
import {translate} from "react-i18next";
import {Form} from "antd/lib/index"
import _ from "lodash"
import Debounced from "../../Debounced"

const FormItem = Form.Item

class HiveDsMetadata extends Component {

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
        return [
            <FormItem label={this.getFieldLabel('db')} key={this.getFieldLabel('db')}>
                {this.renderStringField('db')}
            </FormItem>,
            <FormItem label={this.getFieldLabel('table')} key={this.getFieldLabel('table')}>
                {this.renderStringField('table')}
            </FormItem>]
    }
}

export default translate()(HiveDsMetadata);