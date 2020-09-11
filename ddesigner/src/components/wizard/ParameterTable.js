import React, { Component, Fragment } from 'react';
import { translate } from 'react-i18next'
import { Button, Icon, Tooltip, Checkbox, Table, Form, Input } from 'antd';
import PropTypes from 'prop-types';
import AceEditor from 'react-ace';
import 'brace/mode/scala';
import 'brace/theme/tomorrow';
import 'brace/ext/searchbox';
import _ from 'lodash';

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

class ParameterTable extends Component {

    static propTypes = {
        parameters: PropTypes.array,
        onDataChange: PropTypes.func.isRequired,
        object: PropTypes.object,
        objectType: PropTypes.string.isRequired,
        targetKey: PropTypes.string.isRequired
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
        {
            title: <Tooltip placement="bottom" title={this.props.t('add')}>
                <Button size="small" onClick={() => { this.addNewParameter() }}>
                    +
                </Button>
            </Tooltip>, 
            dataIndex: 'button', align: 'center', width: '20%', render: (text, record) => {
                const editable = this.isEditing(record)
                return (
                    <div>
                        {editable ? (
                            <span>
                                <EditableContext.Consumer>
                                    {form => (
                                        <Fragment>
                                            <Tooltip placement="left" title={this.props.t('save')}>
                                                <Button shape="circle" onClick={() => { this.apply(form, record.key) }}>
                                                    <Icon type="check" size="small" />
                                                </Button>
                                            </Tooltip>
                                            <Tooltip placement="right" title={this.props.t('cancel')}>
                                                <Button shape="circle" onClick={() => { this.cancel(record.key) }}>
                                                    <Icon type="close" size="small" />
                                                </Button>
                                            </Tooltip>
                                        </Fragment>)}
                                </EditableContext.Consumer>
                            </span>
                        ) : (<Fragment>
                                    <Tooltip placement="left" title={this.props.t('edit')}>
                                        <Button shape="circle" onClick={() => { this.edit(record.key) }}>
                                            <Icon type="edit" size="small" />
                                        </Button>
                                    </Tooltip>
                                    <Tooltip placement="right" title={this.props.t('delete')}>
                                        <Button shape="circle" onClick={() => { this.onDelete(record.key) }}>
                                            <Icon type="delete" size="small" />
                                        </Button>
                                    </Tooltip>
                                </Fragment>)}
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

    addNewParameter() {
        const { counter, data } = this.state
        const newData = {
            name: 'name',
            value: 'value',
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
        const {parameters} = this.props
        if(parameters){
            const preparedData = parameters.map((p, i) => ({name: p.name, value: p.value, key: i}))
            this.setState({ data: preparedData, counter: preparedData.length - 1 })
        }
    }

    componentDidUpdate(prevProps, prevState) {
        const { data } = this.state
        const { object, objectType, targetKey, parameters } = this.props
        if(!_.isEqual(prevProps.parameters, parameters)){
            const preparedData = parameters.map((p, i) => ({name: p.name, value: p.value, key: i}))
            this.setState({ data: preparedData, counter: preparedData.length - 1 })
        }
        if(!_.isEqual(data, prevState.data)){
            const newData = data.map(r=>({ 
                name: r.name === "" ? null : r.name , 
                value: r.value === "" ? null : r.value, 
                _type_: objectType 
            }))
            this.props.onDataChange(object, targetKey, newData)
        }
    }

    render() {
        const { data } = this.state
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
                <Table
                    bordered={false}
                    rowKey={record => record.key}
                    dataSource={data}
                    columns={columns}
                    size={'small'}
                    pagination={false}
                    components={components}
                />
            </div>
        )

    }
}

export default translate()(ParameterTable);
