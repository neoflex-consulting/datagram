import React, { Component } from 'react';
import { translate } from 'react-i18next'
import { Row, Col, Button, Select, Avatar, Tooltip, Checkbox, Table, Form, Input } from 'antd';
import resource from "./../../Resource";
import PropTypes from 'prop-types';
import AceEditor from 'react-ace';
import 'brace/mode/scala';
import 'brace/theme/tomorrow';
import 'brace/ext/searchbox';
import _ from 'lodash';
import update from 'immutability-helper'

const ButtonGroup = Button.Group

const FormItem = Form.Item
const EditableContext = React.createContext()

const EditableRow = ({ form, index, ...props }) => (
    <EditableContext.Provider value={form}>
        <tr {...props} />
    </EditableContext.Provider>
)

const EditableFormRow = Form.create()(EditableRow)

class EditableCell extends React.Component {
    getInput = () => {
        if (this.props.inputType === 'boolean') {
            return <Checkbox />
        }
        return <Input />
    }
    render() {
        const {
            editing,
            dataIndex,
            title,
            inputType,
            record,
            index,
            ...restProps
        } = this.props
        return (
            <EditableContext.Consumer>
                {(form) => {
                    const { getFieldDecorator } = form
                    return (
                        <td {...restProps}>
                            {editing ? (
                                <FormItem style={{ margin: 0 }}>
                                    {getFieldDecorator(dataIndex, {
                                        rules: [{
                                            required: false,
                                            message: `Please, Input ${title}!`,
                                        }],
                                        initialValue: record[dataIndex],
                                    })(this.getInput())}
                                </FormItem>
                            ) : restProps.children}
                        </td>
                    )
                }}
            </EditableContext.Consumer>
        )
    }
}

class Run extends Component {

    static propTypes = {
        entity: PropTypes.object,
        cellEntity: PropTypes.object
    }

    constructor(...args) {
        super(...args);
        this.tableGrid = React.createRef();
        this.state = {
            deploymentList: [],
            selectedDeployment: null,
            editingKey: '',
            data: [],
            counter: 0
        }
        this.columns = [{ title: 'Name', dataIndex: 'name', editable: true, align: 'center' },
        { title: 'Value', dataIndex: 'value', editable: true, align: 'center' },
        { title: 'Description', dataIndex: 'description', editable: true, align: 'center' },
        {
            title: '', dataIndex: 'button', align: 'center', width: '20%', render: (text, record) => {
                const editable = this.isEditing(record)
                return (
                    <div>
                        {editable ? (
                            <span>
                                <EditableContext.Consumer>
                                    {form => (
                                        <ButtonGroup>
                                            <Tooltip placement="left" title={this.props.t('save')}>
                                                <Button type="dashed" onClick={() => { this.apply(form, record.key) }}>
                                                    <Avatar src="images/icon-core/save-modern.svg" size={"small"} />
                                                </Button>
                                            </Tooltip>
                                            <Tooltip placement="right" title={this.props.t('cancel')}>
                                                <Button type="dashed" onClick={() => { this.cancel(record.key) }}>
                                                    <Avatar src="images/icon-core/chancel.svg" size={"small"} />
                                                </Button>
                                            </Tooltip>
                                        </ButtonGroup>)}
                                </EditableContext.Consumer>
                            </span>
                        ) : (
                                <ButtonGroup>
                                    <Tooltip placement="left" title={this.props.t('edit')}>
                                        <Button type="dashed" onClick={() => { this.edit(record.key) }}>
                                            <Avatar src="images/icon-core/edit-modern.svg" size={"small"} />
                                        </Button>
                                    </Tooltip>
                                    <Tooltip placement="right" title={this.props.t('delete')}>
                                        <Button type="dashed" onClick={() => { this.onDelete(record.key) }}>
                                            <Avatar src="images/icon-core/delete-modern.svg" size={"small"} />
                                        </Button>
                                    </Tooltip>
                                </ButtonGroup>)}
                    </div>
                )
            }
        }]
    }

    createTextField() {
        return (
            <AceEditor
                mode={'scala'}
                width={''}
                height={'10vh'}
                theme={'tomorrow'}
                fontSize={15}
                editorProps={{ $blockScrolling: Infinity }}
                value={'Ok!'}
                showPrintMargin={false}
                showGutter={false}
                focus={false}
                readOnly={true}
                minLines={5}
                highlightActiveLine={false}
            />
        )
    }

    getDeploymentList() {
        const { entity } = this.props.context
        resource.query("/api/teneo/select/from%20rt.TransformationDeployment%20where%20transformation.e_id=" + entity.e_id).then(json => {
            const data = json.length > 0 ?
                json.map(d => d.parameters.map(
                    (p, index) => ({ name: p.name, value: p.value, description: p.description ? p.description : null, key: index, deploymentName: d.name })
                )) : []
            this.setState({ data: _.flatten(data), counter: data.length, deploymentList: json, selectedDeployment: json.length > 0 ? json[0].name : null })
        })
    }

    runTransformation() {
        const { deploymentList, selectedDeployment, data } = this.state
        if (this.state.selectedDeployment) {
            const trd = deploymentList.find((d) => d.name === selectedDeployment)
            const params = {}
            data.forEach(p => params[p.name] = p.value)
            resource.call({ ...this.props.activeObject, trd, params }, 'runit').then(json => 
                json.result ?
                    resource.logInfo('Successful run.')
                :
                    json.problems ? json.problems.map(p => resource.logError(<div> {p.constraint} <br /> {p.context} <br /> {p.message} <br /></div>)) : 'Undefined error.'
                
            )
        } else {
            resource.error('Deployment is undefined.')
        }
    }

    saveParameters() {
        const { deploymentList, selectedDeployment, data } = this.state
        if (this.state.selectedDeployment) {
            const trd = deploymentList.find((d) => d.name === selectedDeployment)
            const newParams = data.map(p => ({_type_: 'etl.Property', name: p.name, value: p.value, t_id: trd.e_id, description: p.description})) 
            const updatedTrd = update(trd, { $merge: { parameters: newParams } })
            resource.saveEntity(updatedTrd)
        }
    }

    addNewParameter() {
        const { counter, data } = this.state
        const newData = {
            name: 'name',
            value: 'value',
            description: '',
            key: counter + 2
        }
        this.setState({ data: [...data, newData], counter: counter + 2, })
    }

    isEditing = (record) => {
        return record.key === this.state.editingKey
    }

    edit(key) {
        this.setState({ editingKey: key })
    }

    cancel = () => {
        this.setState({ editingKey: '' })
    }

    onDelete = (key) => {
        const newData = [...this.state.data];
        this.setState({ data: newData.filter(item => item.key !== key) })
    }

    apply(form, key) {
        form.validateFields((error, row) => {
            if (error) {
                return
            }
            const newData = [...this.state.data]
            const index = newData.findIndex(item => key === item.key)
            if (index > -1) {
                const item = newData[index]
                newData.splice(index, 1, {
                    ...item,
                    ...row,
                })
                this.setState({ data: newData, editingKey: '' })
            } else {
                newData.push(row)
                this.setState({ data: newData, editingKey: '' })
            }
        })
    }

    componentDidMount() {
        if (this.props.activeObject) {
            resource.call(this.props.activeObject, 'checkTRD').then(json =>
                json.result ? this.getDeploymentList() : undefined)
        }
    }

    render() {
        const { deploymentList, selectedDeployment, data } = this.state
        const { t } = this.props
        const Option = Select.Option
        const components = {
            body: {
                row: EditableFormRow,
                cell: EditableCell,
            },
        }

        const columns = this.columns.map((col) => {
            if (!col.editable) {
                return col
            }
            return {
                ...col,
                onCell: record => ({
                    record,
                    inputType: col.dataIndex === 'expression' ? 'boolean' : 'text',
                    dataIndex: col.dataIndex,
                    title: col.title,
                    editing: this.isEditing(record),
                }),
            }
        })

        return (
            <div>
                <Row>
                    <Col span={4}>
                        <Tooltip placement="bottom" title={t('add')}>
                            <Button style={{ marginTop: "5px", marginLeft: "2px" }} onClick={() => { this.addNewParameter() }}>
                                <Avatar src="images/icon-core/plus-modern.svg" size={"small"} />
                            </Button>
                        </Tooltip>
                    </Col>
                    <Col pull={3} span={4}>
                        <Tooltip placement="bottom" title={t('save')}>
                            <Button style={{ marginTop: "5px" }} onClick={() => { this.saveParameters() }}>
                                <Avatar src="images/icon-core/save-modern.svg" size={"small"} />
                            </Button>
                        </Tooltip>
                    </Col>
                    <Col pull={6} span={4}>
                        <Tooltip placement="bottom" title={t('run')}>
                            <Button style={{ marginTop: "5px" }} onClick={() => { this.runTransformation() }}>
                                <Avatar src="images/icon-core/arrow-right-modern.svg" size={"small"} />
                            </Button>
                        </Tooltip>
                    </Col>
                    <Col pull={9} span={4}>
                        <Tooltip placement="bottom" title={t('deployment')}>
                            <Select
                                showSearch
                                style={{ width: "300px", marginTop: "5px" }}
                                value={selectedDeployment}
                                optionFilterProp="children"
                                onChange={(deploymentName) => {
                                    this.setState({ selectedDeployment: deploymentName })
                                }}
                            >
                                {deploymentList && deploymentList.map((dep, index) => {
                                    return <Option key={`${dep.name}_${index}`} value={dep.name}>{dep.name}</Option>
                                })}
                            </Select>
                        </Tooltip>
                    </Col>
                </Row>
                <br />
                <Table
                    bordered
                    rowKey={'params'}
                    dataSource={data.filter( parameter => parameter.deploymentName === selectedDeployment )}
                    columns={columns}
                    size='small'
                    pagination={false}
                    components={components}
                />
            </div>
        )

    }
}

export default translate()(Run);
