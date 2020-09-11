import React, {Component} from 'react';
import {translate} from 'react-i18next'
import {Form, Select, Tooltip, Button, Avatar, Input} from 'antd'
import _ from 'lodash'
import resource from "./../../../Resource";

const FormItem = Form.Item
const Option = Select.Option

class LinkedMetadata extends Component {

    constructor(props) {
        super(props)
        this.state = {selects: {}}
    }

    componentDidMount() {
        resource.getSimpleSelect("sse.AbstractDataset", ["name", "shortName"]).then(linkTo=>{
            this.setState({selects: {linkTo}})
        })
    }

    // FIXME 1. Copy/paste from Metadata (rewrite to static util methods with parameters)
    // FIXME 2. doesn't display anyway
    getFieldLabel(name) {
        const {t, entity} = this.props
        return t(entity._type_ + '.attrs.' + name + '.caption', {ns: 'classes'})
    }

    render() {
        const {entity, updateEntity, t} = this.props
        const fieldName = "linkTo"
        const editButtonWidth = _.get(entity, [fieldName, 'e_id']) ? 36 : 0
        const displayField = "name"
        const selects = _.get(this.state.selects, fieldName, [])
        return <FormItem key={fieldName} label={this.getFieldLabel(fieldName)}>
            <Input.Group compact>
                <Select className="ant-select-no-padding" disabled={false} showSearch
                        style={{width: `calc(100% - ${editButtonWidth}pt)`}}
                        allowClear
                        id={fieldName} value={_.get(entity, [fieldName, 'e_id'])}
                        onChange={value => {
                            updateEntity({[fieldName]: selects.find(ent => ent.e_id === value)})
                        }}
                        filterOption={(input, option) => option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0}
                >
                    {_.sortBy(selects, (o) => _.get(o, 'displayField', displayField).toLowerCase()).map(ent =>
                        <Option key={ent.e_id} value={ent.e_id}>{_.get(ent, 'displayField', ent.name)}</Option>
                    )}
                </Select>
                {editButtonWidth > 0 &&
                <Tooltip placement="top" title={t("edit")}>
                    <Button type="dashed" placement="" onClick={() => {
                        const {_type_, e_id, name} = _.get(entity, [fieldName])
                        this.props.selectObject({_type_, e_id, name})
                    }}><Avatar className='avatar-button-property' size='small' src='images/icon-core/edit-modern.svg'/></Button>
                </Tooltip>}
            </Input.Group>
        </FormItem>
    }
}

export default translate()(LinkedMetadata);