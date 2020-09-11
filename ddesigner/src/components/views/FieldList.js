import React, {Component} from 'react';
import {translate} from "react-i18next";
import resource from "../../Resource";
import {getEntityClassFeature, getModel} from '../../model.js';
import {
    Collapse,
    Form,
    Input,
    Row,
    Col,
    Button,
    Icon,
    Dropdown,
    Menu,
    InputNumber,
    Select,
    DatePicker,
    Avatar,
    Tooltip,
    Checkbox
} from 'antd'
import update from 'immutability-helper'
import _ from 'lodash'
import moment from 'moment'
import DisplayList from './DisplayList';
import {instantiate} from '../../utils/meta'
import Debounced from '../Debounced'
import {copyIntoClipboard} from '../../utils/clipboard'
import {colorList} from '../../utils/consts'

const FormItem = Form.Item
const {TextArea} = Input
const Panel = Collapse.Panel
const ButtonGroup = Button.Group
const Option = Select.Option

class FieldList extends Component {

    constructor(...args) {
        super(...args);
        this.state = {selects: {}, fileNames: {}}
    }

    getEntityClassFeature(typeName, entity, feature) {
        return this.props.getEntityClassFeature ?
            this.props.getEntityClassFeature(typeName, entity, feature) :
            getEntityClassFeature(getModel(), typeName, entity, feature)
    }

    propsChanged(props) {
        let {fields} = props
        this.collectSelects(fields)
    }

    listEval(listeval) {
        const {state, props} = this // eslint-disable-line no-unused-vars
        return eval(listeval) // eslint-disable-line no-eval
    }

    collectSelects(fields) {
        fields.forEach(f => {
            if (f.type === "line" || f.type === "set") {
                this.collectSelects(f.fields)
            }
            else if (f.type === "select" || f.type === "multi" || f.type === "selectString" || f.type === "multiString") {
                const displayField = _.get(f, "displayField", "name")
                if (f.filter === undefined) {
                    if (f.listeval !== undefined) {
                        // eval list on field render
                    }
                    else {
                        resource.getSimpleSelect(f.entityType, [displayField]).then(list => {
                            this.setState(update(this.state, {selects: {[f.name]: {$set: list}}}))
                        })
                    }
                }
                else if (this.props.entity.e_id) {
                    resource.getEntityAttribute(this.props.entity._type_, f.dataModel, this.props.entity.e_id).then(e_id => {
                        return resource.getSimpleSelect(f.entityType, [displayField], {[f.filter]: e_id})
                    }).then(list => {
                        this.setState(update(this.state, {selects: {[f.name]: {$set: list}}}))
                    })
                }
            }
        })
    }

    componentDidMount() {
        this.propsChanged(this.props)
    }

    componentWillReceiveProps(nextProps) {
        if (!_.isEqual(nextProps.fields, this.props.fields) || nextProps.entity._type_ !== this.props.entity._type_ || nextProps.entity.e_id !== this.props.entity.e_id) {
            this.propsChanged(nextProps)
        }
    }

    getReadOnly(field) {
        return field.readOnly === true || this.props.readOnly === true ||
            (field.type === "form" && field.entityType && this.getEntityClassFeature(field.entityType, null, "readOnly") === true)
    }

    renderLineField(field) {
        if (field.fields.length === 0) {
            return null
        }
        const colSpan = 24 / field.fields.length
        return <Row key={"line_" + field.fields[0].name} gutter={24}>{
            field.fields.map(f => <Col key={f.name} span={colSpan}>{this.renderField(f)}</Col>)
        }</Row>
    }

    getFieldLabel(field) {
        const {t, entity, actionName} = this.props
        if (!actionName) {
            return t(entity._type_ + '.attrs.' + field.name + '.caption', {ns: 'classes'})
        }
        else {
            return t(entity._type_ + '.ops.' + actionName + '.params.' + field.name + '.caption', {ns: 'classes'})
        }
    }

    renderStringColorField(field) {
        const {entity, updateEntity} = this.props
        const buttonWidth = 36
        const colorMenu = <Menu onClick={e => {
            const updates = {[field.name]: e.key}
            const {onChange} = field
            if (onChange) onChange(updates, e.key, field, entity, this.props)
            updateEntity(updates)
        }}>
            {colorList.map(c => <Menu.Item key={c}>
                    <div style={{ backgroundColor: c, width: '30px', height: '30px' }}/>
              </Menu.Item>
            )}
        </Menu>

        return <FormItem key={field.name} label={this.getFieldLabel(field)}>
            <Input.Group compact>
                <Debounced
                    id={field.name} value={entity[field.name]}
                    disabled={this.getReadOnly(field)}
                    style={
                        {width: `calc(100% - ${buttonWidth}pt)`}}
                    onChange={e => {
                        const updates = {[field.name]: e.target.value}
                        const {onChange} = field
                        if (onChange) onChange(updates, e.target.value, field, entity, this.props)
                        updateEntity(updates)
                    }}/>
                    <Dropdown overlay={colorMenu}>
                        <Button overlay={colorMenu} type="dashed" placement="" style={{ backgroundColor: entity[field.name] }}/>
                    </Dropdown>
            </Input.Group>
        </FormItem>;
    }

    renderStringField(field) {
        const {t, entity, updateEntity} = this.props
        const buttonWidth = field.isURL && _.get(entity, [field.name]) ? 36 : 0
        return <FormItem key={field.name} label={this.getFieldLabel(field)}>
            <Input.Group compact>
                <Debounced
                    id={field.name} value={entity[field.name]}
                    disabled={this.getReadOnly(field)}
                    style={
                        {width: `calc(100% - ${buttonWidth}pt)`}} //`
                    onChange={e => {
                        const updates = {[field.name]: e.target.value}
                        const {onChange} = field
                        if (onChange) onChange(updates, e.target.value, field, entity, this.props)
                        updateEntity(updates)
                    }}/>
                {buttonWidth > 0 &&
                <Tooltip placement="top" title={t("open")}>
                    <Button type="dashed" placement="" onClick={() => {
                        var win = window.open(_.get(entity, [field.name]), '_blank');
                        win.focus();
                    }}><Avatar className='avatar-button-property' size='small' src='images/icon-core/link.svg'/></Button>
                </Tooltip>}
            </Input.Group>
        </FormItem>;
    }

    renderDateField(field) {
        const {entity, updateEntity} = this.props
        return <FormItem key={field.name} label={this.getFieldLabel(field)}>
            <DatePicker
                disabled={this.getReadOnly(field)}
                id={field.name}
                value={entity[field.name] && moment(entity[field.name])}
                onChange={date => {
                    updateEntity({[field.name]: date ? date.toDate() : undefined})
                }}
            />
        </FormItem>
    }

    renderDatetimeField(field) {
        const {entity, updateEntity} = this.props
        return <FormItem key={field.name} label={this.getFieldLabel(field)}>
            <DatePicker
                disabled={this.getReadOnly(field)}
                showTime
                format="YYYY-MM-DD HH:mm:ss"
                id={field.name}
                value={entity[field.name] && moment(entity[field.name])}
                onChange={date => {
                    updateEntity({[field.name]: date ? date.toDate() : undefined})
                }}
            />
        </FormItem>
    }

    renderEnumField(field) {
        const {entity, updateEntity} = this.props
        return <FormItem key={field.name} label={this.getFieldLabel(field)}>
            <Select allowClear id={field.name} value={entity[field.name]} disabled={this.getReadOnly(field)}
                    onChange={value => {
                        updateEntity({[field.name]: value})
                    }}>
                {field.options.map(opt => <Option key={opt} value={opt}>{opt}</Option>)}
            </Select>
        </FormItem>
    }

    renderSelectField(field) {
        const {entity, updateEntity, t} = this.props
        const editButtonWidth = _.get(entity, [field.name, 'e_id']) ? 36 : 0
        const displayField = _.get(field, "displayField", "name")
        const selects = _.sortBy(field.listeval ? this.listEval(field.listeval) : _.get(this.state.selects, field.name, []), (o) => _.get(o, 'displayField', displayField).toLowerCase())
        function getIndex(list, value) {
            if(!value) {
                return value
            }
            var idx = list.indexOf(value);
            if(idx > -1) {
                return idx;
            }
            list.forEach((item, index)=>{
                if(item.e_id && item.e_id === value.e_id) {
                    idx = index
                }
            })
            if(idx === -1) {
                list.forEach((item, index)=>{
                    if(item.name && item.name === value.name) {
                        idx = index
                    }
                })
            }
            return idx
        }
        return <FormItem key={field.name} label={this.getFieldLabel(field)}>
            <Input.Group compact>
                <Select className="ant-select-no-padding" disabled={this.getReadOnly(field)} showSearch
                        style={{width: `calc(100% - ${editButtonWidth}pt)`}} //`
                        allowClear
                        id={field.name} value={getIndex(selects, _.get(entity, [field.name]))}
                        onChange={value => {
                            var val = selects[value]
                            const updates = {[field.name]: val}
                            const {onChange} = field
                            if (onChange) onChange(updates, val, field, entity, this.props)
                            updateEntity(updates)
                        }}
                        filterOption={(input, option) => option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0}
                >
                    {selects.map((ent, index) =>
                        <Option key={index} value={index}>{_.get(ent, 'displayField', ent.name)}</Option>
                    )}
                </Select>
                {editButtonWidth > 0 &&
                <Tooltip placement="top" title={t("edit")}>
                    <Button type="dashed" placement="" onClick={() => {
                        const {_type_, e_id, name} = _.get(entity, [field.name])
                        this.props.selectObject({_type_, e_id, name})
                    }}><Avatar className='avatar-button-property' size='small' src='images/icon-core/edit-modern.svg'/></Button>
                </Tooltip>}
            </Input.Group>
        </FormItem>
    }

    renderSelectStringField(field) {
        const {entity, updateEntity} = this.props
        const displayField = _.get(field, "displayField", "name")
        const selects = field.listeval ? this.listEval(field.listeval) : _.get(this.state.selects, field.name, [])
        return <FormItem key={field.name} label={this.getFieldLabel(field)}>
            <Select className="ant-select-no-padding" disabled={this.getReadOnly(field)} showSearch
                    allowClear
                    id={field.name}
                    value={_.get(entity, [field.name])}
                    onChange={value => {
                        updateEntity({[field.name]: value})
                    }}
                    filterOption={(input, option) => option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0}
            >
                {_.sortBy(selects, (o) => _.get(o, displayField, '').toLowerCase()).map((ent, index) =>
                    <Option key={index} value={_.get(ent, displayField)}>{_.get(ent, displayField)}</Option>
                )}
            </Select>
        </FormItem>
    }

    renderStringList(field) {
        const {entity, updateEntity} = this.props
        return <FormItem key={field.name} label={this.getFieldLabel(field)}>
            <Select className="ant-select-no-padding" disabled={this.getReadOnly(field)}
                    mode="tags"
                    id={field.name}
                    value={_.get(entity, [field.name])}
                    onChange={values => {
                        updateEntity({[field.name]: values})
                    }}
                    tokenSeparators={[',']}
            />
        </FormItem>
    }

    renderMultiField(field) {
        const {entity, updateEntity} = this.props
        const displayField = _.get(field, "displayField", "name")
        const selects = field.listeval ? this.listEval(field.listeval) : _.get(this.state.selects, field.name, [])
        return <FormItem key={field.name} label={this.getFieldLabel(field)}>
            <Select className="ant-select-no-padding"
                    disabled={this.getReadOnly(field)}
                    showSearch
                    allowClear
                    mode="multiple"
                    id={field.name}
                    value={_.get(entity, [field.name], []).map(ent=>_.get(ent, displayField, ''))}
                    onChange={values => {
                        updateEntity({[field.name]: values.map(name=>selects.find(ent=>_.get(ent, displayField, '') === name))})
                    }}
                    filterOption={(input, option) => option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0}
            >
                {_.sortBy(selects, (o) => _.get(o, displayField, '').toLowerCase()).map((ent, index) =>
                    <Option key={index} value={_.get(ent, displayField, '')}>{_.get(ent, displayField, '')}</Option>)}
            </Select>
        </FormItem>
    }

    renderMultiStringField(field) {
        const {entity, updateEntity} = this.props
        const displayField = _.get(field, "displayField", "name")
        const selects = field.listeval ? this.listEval(field.listeval) : _.get(this.state.selects, field.name, [])
        return <FormItem key={field.name} label={this.getFieldLabel(field)}>
            <Select className="ant-select-no-padding"
                    disabled={this.getReadOnly(field)}
                    showSearch
                    allowClear
                    mode="multiple"
                    id={field.name}
                    value={_.get(entity, [field.name])}
                    onChange={values => {
                        updateEntity({[field.name]: values})
                    }}
                    filterOption={(input, option) => option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0}
            >
                {_.sortBy(selects, (o) => _.get(o, displayField, '').toLowerCase()).map(ent =>
                    <Option key={_.get(ent, displayField)} value={_.get(ent, displayField)}>{_.get(ent, displayField)}</Option>
                )}
            </Select>
        </FormItem>
    }

    renderPasswordField(field) {
        const {entity, updateEntity} = this.props
        return <FormItem key={field.name} label={this.getFieldLabel(field)}>
            <Input
                disabled={this.getReadOnly(field)}
                id={field.name}
                prefix={<Icon type="lock" style={{color: 'rgba(0,0,0,.25)'}}/>}
                type="password"
                value={entity[field.name]}
                onChange={e => {
                    updateEntity({[field.name]: e.target.value})
                }}
            />
        </FormItem>
    }

    renderNumberField(field) {
        const {entity, updateEntity} = this.props
        return <FormItem key={field.name} label={this.getFieldLabel(field)}>
            <InputNumber id={field.name} value={entity[field.name]} disabled={this.getReadOnly(field)}
                         onChange={value => {
                             updateEntity({[field.name]: value})
                         }}/>
        </FormItem>
    }

    renderTextField(field) {
        const {entity, updateEntity} = this.props
        return <FormItem key={field.name} label={this.getFieldLabel(field)}>
            <Debounced Component={TextArea} autosize={{minRows: 2, maxRows: 40}} id={field.name} disabled={this.getReadOnly(field)}
                      value={entity[field.name]} onChange={e =>
                updateEntity({[field.name]: e.target.value})
            }/>
        </FormItem>
    }

    renderFormField(field) {
        const {t, entity, updateEntity} = this.props
        const embedded = entity[field.name]
        const embeddedWithContext = {...(embedded || {}), __parent: entity}
        const createMenu = <Menu onClick={e => {
            e.domEvent.stopPropagation()
            updateEntity({[field.name]: instantiate(e.key)})
        }}>
            {this.getEntityClassFeature(field.entityType, embeddedWithContext, "successors").map(embeddedType =>
                <Menu.Item
                    key={embeddedType}>{t(`${embeddedType}.caption`, {ns: 'classes'})}</Menu.Item>)}
        </Menu>; //`
        const attrCaption =
            <Row type="flex" justify="space-between">
                <Col>{this.getFieldLabel(field) + (embedded === undefined?"":": " + t(`${embedded._type_}.caption`, /*`*/ {ns: 'classes'}))}</Col>
                <Col>{!this.getReadOnly(field) &&
                <ButtonGroup className="formview-collapse-button">
                    {embedded === undefined &&
                    <Dropdown overlay={createMenu}>
                        <Button type="dashed" size="small" placement="" onClick={e => {
                            e.stopPropagation()
                        }}>
                            <Avatar className='avatar-add' src='images/icon-core/file-add.svg'/>
                        </Button>
                    </Dropdown>}
                    {embedded !== undefined &&
                    <Button type="dashed" size="small" placement="" onClick={e => {
                        e.stopPropagation()
                        updateEntity({[field.name]: undefined})
                    }}><Icon type="delete"/></Button>
                    }
                </ButtonGroup>}
                </Col>

            </Row>

        return (<Collapse key={field.name}>
            <Panel header={attrCaption}>
                {embedded &&
                <FieldList key={field.name + "FieldList"} entity={embeddedWithContext} t={t}
                           fields={this.getEntityClassFeature(null, embeddedWithContext, "fields") || []}
                           selectObject={this.props.selectObject}
                           updateEntity={e => updateEntity({[field.name]: update(embedded, {$merge: e})})}
                           readOnly={this.getReadOnly(field)}
                           getEntityClassFeature={this.props.getEntityClassFeature}
                           context={this.props.context}
                           updateContext={(val, cb) => this.props.updateContext(val, cb)}
                />}
            </Panel>
        </Collapse>)
    }

    renderSetField(field) {
        const {t, entity} = this.props
        const attrCaption = t(entity._type_ + '.groups.' + field.name + '.caption', {ns: 'classes'})
        return (<Collapse key={field.name}>
            <Panel header={attrCaption}>
                {field.fields.map(field => this.renderField(field))}
            </Panel>
        </Collapse>)
    }

    renderJsonField(field) {
        const {entity, updateEntity} = this.props
        return <FormItem key={field.name} label={this.getFieldLabel(field)}>
            <Input id={field.name} value={JSON.stringify(entity[field.name])} disabled={this.getReadOnly(field)}
                   onChange={e => {
                       updateEntity({[field.name]: JSON.parse(e.target.value)})
                   }}/>

        </FormItem>
    }

    renderFileField(field) {
        const {updateEntity} = this.props
        const fileInput = (
            <label>
                <Avatar className="button-avatar" src="images/icon-core/upload.svg" size={"small"}/>
                <Input type="file" style={{display: "none"}} disabled={this.getReadOnly(field)}
                       id={field.name + "_file"}
                       onChange={e => {
                           const file = e.target.files[0]
                           updateEntity({[field.name]: file})
                           this.setState({fileNames: update(this.state.fileNames, {$merge: {[field.name]: file ? file.name.replace(/\\/g, '/').replace(/.*\//, '') : undefined}})})
                       }}
                       onClick={e => {
                           this.setState({fileNames: update(this.state.fileNames, {$merge: {[field.name]: undefined}})})
                       }}
                />
            </label>
        )
        return <FormItem key={field.name} label={this.getFieldLabel(field)}>
            <Input addonBefore={fileInput} id={field.name + "_name"} value={this.state.fileNames[field.name]}
                   RreadOnly={true}/>

        </FormItem>
    }

    renderBooleanField(field) {
        const {entity, updateEntity} = this.props
        return <FormItem key={field.name}>
            <Checkbox
                disabled={this.getReadOnly(field)}
                checked={entity[field.name]}
                onChange={e => {
                    updateEntity({[field.name]: e.target.checked})
                }}
            >{this.getFieldLabel(field)}</Checkbox>

        </FormItem>
    }

    renderTableField(field) {
        const {t, entity, updateEntity} = this.props
        const idAndType = [{"Header": "e_id", "accessor": "e_id", "indexKey": "e_id", "sortable": true, "show": false},
            {"Header": "_type_", "accessor": "_type_", "indexKey": "_type_", "sortable": true, "show": false}]
        const context = {__parent: entity}
        const boolField = this.getEntityClassFeature(field.entityType, context, "fields")
        const columns = this.getEntityClassFeature(field.entityType, context, "columns")
        const checkBoolRow = columns.map(c => {
            const field = boolField.find(f => f.name === c.accessor)
            const expCol = {
                Cell: (row) =>
                    (
                        <div style={{marginLeft: "50%"}}>
                            <Checkbox checked={_.get(row.original, field.name, false)} disabled/>
                        </div>
                    )
            }
            return field && field.type === "boolean" ? {...c, ...expCol} : c
        })
        const col = _.concat(checkBoolRow, idAndType)
        const createMenu = <Menu onClick={e => {
            e.domEvent.stopPropagation();
            updateEntity({[field.name]: update(entity[field.name] || [], {$push: [instantiate(e.key)]})});
        }}>
            {this.getEntityClassFeature(field.entityType, context, "successors").map(embeddedType => <Menu.Item
                key={embeddedType}>{t(`${embeddedType}.caption`, {ns: 'classes'})}</Menu.Item>)}
        </Menu>; //`
        const copyBtn = <div>
        {field.name === "fields" &&
            <Tooltip placement="top" title={t("copy")}>
                <Button type="dashed" size="small" placement="" onClick={e => {
                                e.stopPropagation()
                                if(field.name === "fields") {
                                    var deleteProp = function(value, propName, parent) {
                                        if (typeof value !== "object") {
                                            if (!Array.isArray(value)) {
                                                return value
                                            }
                                        }
                                        if (!Array.isArray(value)) {
                                            if (value) {
                                                if (value.hasOwnProperty(propName)) {
                                                    delete value[propName]
                                                }
                                                Object.entries(value).forEach((e) => {
                                                    deleteProp(e[1], propName, parent + "." + e[0])
                                                })
                                            }
                                        } else {
                                            value.forEach((node, index) => {
                                                deleteProp(node, propName, parent + '[' + index + ']')
                                            })
                                        }
                                        return value
                                    }

                                    var a = _.cloneDeepWith(entity[field.name], (value) => {
                                        deleteProp(value, "e_id", "field")
                                        deleteProp(value, "dataSet", "field")
                                        return value;
                                    })
                                    copyIntoClipboard(JSON.stringify(a, null, '    '))
                                }
                            }}><Icon type="copy"/></Button>
                    </Tooltip>}
        </div>
        const pasteBtn = <div>
        {field.name === "fields" && !this.getReadOnly(field) &&
            <Tooltip placement="top" title={t("paste")}>
                <Button type="dashed" size="small" placement="" onClick={e => {
                                e.stopPropagation()
                                if(field.name === "fields") {
                                    navigator.clipboard.readText()
                                      .then(text => {
                                          try {
                                              var copiedFields = JSON.parse(text)
                                              if(Array.isArray(copiedFields)) {
                                                  updateEntity({[field.name]: update([], {$push: copiedFields})})
                                              }
                                          } finally {

                                          }
                                      })
                                }
                            }}><Icon type="export"/></Button>
            </Tooltip>}
        </div>

        const attrCaption =
            <Row type="flex" justify="space-between">
                <Col>{this.getFieldLabel(field)}</Col>
                <Col>{copyBtn}</Col>
                <Col>{pasteBtn}</Col>
                <Col>
                {!this.getReadOnly(field) &&
                <ButtonGroup className="formview-collapse-button">
                    <Dropdown overlay={createMenu}>
                        <Button type="dashed" size="small" placement="" onClick={e => {
                            e.stopPropagation()
                        }}>
                            <Avatar className='avatar-add' src='images/icon-core/file-add.svg'/>
                        </Button>
                    </Dropdown>
                </ButtonGroup>}
                </Col>

            </Row>

        return (
            <Collapse key={field.name} style={{"marginBottom": "10px"}}>
                <Panel header={attrCaption}>
                    <DisplayList
                        readOnly={this.getReadOnly(field)}
                        list={this.props.entity[field.name]}
                        storageId={"fv_" + field.name + "_" + this.props.entity._type_}
                        columns={col}
                        SubComponent={(row) =>
                            <Row>
                                <Col span={1}/>
                                <Col span={22}>
                                    <FieldList readOnly={this.getReadOnly(field)} updateEntity={rowEntity => {
                                        const list = entity[field.name]
                                        const idx = list.indexOf(row.original)
                                        if (idx < 0) return;
                                        updateEntity({[field.name]: update(list, {[idx]: {$merge: rowEntity}})})
                                    }}
                                               entity={{...row.original, ...context}} t={t}
                                               fields={this.getEntityClassFeature(null, {...row.original, ...context}, "fields") || []}
                                               selectObject={this.props.selectObject}
                                               getEntityClassFeature={this.props.getEntityClassFeature}
                                               context={this.props.context}
                                    />
                                </Col>
                                <Col span={1}/>
                            </Row>

                        }
                        controlColumn={{
                            Header: '',
                            accessor: 'e_id',
                            Cell: row => (
                                <ButtonGroup className="pull-right">
                                    <Tooltip title={t("up")}>
                                        <Button type="dashed" size="small" placement=""
                                                disabled={this.getReadOnly(field)}
                                                onClick={() => {
                                                    const list = entity[field.name].slice()
                                                    const idx = list.indexOf(row.original)
                                                    if (idx > 0) {
                                                        updateEntity({
                                                            [field.name]: [
                                                                ...list.slice(0, idx - 1),
                                                                list[idx],
                                                                list[idx - 1],
                                                                ...list.slice(idx + 1)
                                                            ]
                                                        })
                                                    }
                                                }
                                                }
                                        >
                                            <Avatar className="button-avatar" src="images/icon-core/arrow-up-modern.svg"
                                                    size={"small"}/>
                                        </Button>
                                    </Tooltip>
                                    <Tooltip title={t("down")}>
                                        <Button type="dashed" size="small" placement=""
                                                disabled={this.getReadOnly(field)}
                                                onClick={() => {
                                                    const list = entity[field.name].slice()
                                                    const idx = list.indexOf(row.original)
                                                    if (idx >= 0 && idx < list.length - 1) {
                                                        updateEntity({
                                                            [field.name]: [
                                                                ...list.slice(0, idx),
                                                                list[idx + 1],
                                                                list[idx],
                                                                ...list.slice(idx + 2)
                                                            ]
                                                        })
                                                    }
                                                }
                                                }
                                        >
                                            <Avatar className="button-avatar"
                                                    src="images/icon-core/arrow-down-modern.svg" size={"small"}/>
                                        </Button>
                                    </Tooltip>
                                    <Tooltip title={t("delete")}>
                                        <Button type="dashed" size="small" placement=""
                                                disabled={this.getReadOnly(field)}
                                                onClick={() => {
                                                    const list = entity[field.name].slice()
                                                    const idx = list.indexOf(row.original)
                                                    if (idx >= 0) {
                                                        updateEntity({
                                                            [field.name]: [
                                                                ...list.slice(0, idx),
                                                                ...list.slice(idx + 1)
                                                            ]
                                                        })
                                                    }
                                                }
                                                }
                                        >
                                            <Avatar className="button-avatar" src="images/icon-core/delete-modern.svg"
                                                    size={"small"}/>
                                        </Button>
                                    </Tooltip>
                                </ButtonGroup>
                            ),
                            filterable: false,
                            sortable: false,
                            resizable: false,
                            width: 120
                        }}
                    />
                </Panel>
            </Collapse>
        )
    }

    renderField(field) {
        return (field.type === "string" && field.name === "colour" && this.renderStringColorField(field)) ||
            (field.type === "string" && this.renderStringField(field)) ||
            (field.type === "text" && this.renderTextField(field)) ||
            (field.type === "line" && this.renderLineField(field)) ||
            (field.type === "form" && this.renderFormField(field)) ||
            (field.type === "set" && this.renderSetField(field)) ||
            (field.type === "number" && this.renderNumberField(field)) ||
            (field.type === "password" && this.renderPasswordField(field)) ||
            (field.type === "enum" && this.renderEnumField(field)) ||
            (field.type === "select" && this.renderSelectField(field)) ||
            (field.type === "selectString" && this.renderSelectStringField(field)) ||
            (field.type === "multi" && this.renderMultiField(field)) ||
            (field.type === "multiString" && this.renderMultiStringField(field)) ||
            (field.type === "date" && this.renderDateField(field)) ||
            (field.type === "datetime" && this.renderDatetimeField(field)) ||
            (field.type === "table" && this.renderTableField(field)) ||
            (field.type === "boolean" && this.renderBooleanField(field)) ||
            (field.type === "file" && this.renderFileField(field)) ||
            (field.type === "stringList" && this.renderStringList(field)) ||
            this.renderJsonField(field)
    }

    render() {
        return (this.props.fields || []).filter(field => !field.hidden || this.props.showHidden).map(field => this.renderField(field))
    }
}

export default translate()(FieldList);
