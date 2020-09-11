import React, {Component, } from 'react'
import {translate} from "react-i18next"
import {Avatar, Button, Checkbox, Col, Dropdown, Form, Icon, Input, Menu, Modal, Row, Table, Tooltip} from 'antd'
import _ from 'lodash'
import {addByName, displayColumnType, isExpandableType, removeByKey} from './Structure'
import ColumnType from "./ColumnType"
import {instantiate} from '../../../utils/meta'
import update from 'immutability-helper';

const ButtonGroup = Button.Group
const FormItem = Form.Item
const EditableContext = React.createContext()

const EditableRow = ({form, index, ...props}) => (
    <EditableContext.Provider value={form}>
        <tr {...props} />
    </EditableContext.Provider>
)

const EditableFormRow = Form.create()(EditableRow)

class EditableCell extends React.Component {

    updateEntityColumns = (record, e) => {
        const newColumns = _.cloneDeepWith(this.props.entity.columns, (c)=>{
            if(c && c.columnName && c.columnName === record.name){
                if(e.target && e.target.id === "isNullable"){
                    c.columnType.isNullable = e.target.checked
                }
                if(e.target && e.target.id === "name"){
                    c.columnName = e.target.value
                }
                if(e.target && e.target.id === "colDescription"){
                    c.columnType.description = e.target.value
                }
            }
        })
        this.form.validateFields((error, values) => {
            if (error) {
                return
            }
            this.props.updateEntity(update(this.props.entity, { $set: { columns: newColumns } }))
        })    
    }

    getInput = () => {
        const { dataIndex, record } = this.props
        if (dataIndex === 'type') {
            return <ColumnType {...this.props}/>
        }
        if (dataIndex === 'isNullable') {
            return <Checkbox 
                checked={record.isNullable} 
                onChange={(e)=>{
                    this.updateEntityColumns(record, e)
                }}
                />
        }
        return <Input 
            onChange={(e) => {
                this.updateEntityColumns(record, e)
            }}
            />
    }

    render() {
        const {
            editing,
            dataIndex,
            title,
            inputType,
            record,
            index,
            handleSave,
            ...restProps
        } = this.props
        return (
            <EditableContext.Consumer>
                {(form) => {
                    const {getFieldDecorator} = form
                    this.form = form
                    return (
                        <td {...restProps}>
                            {editing ? (
                                <FormItem style={{margin: 0}}>
                                    {getFieldDecorator(dataIndex, {
                                        initialValue: record[dataIndex]
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

class StructGrid extends Component {

    constructor(props) {
        super(props)
        this.state = {editingKey: ''}
        this.newColumnCount = 0
        this.keyCounter = 1
    }

    render() {
        return <div>
            <Row type="flex" justify="end" align="middle">
                <Col span={2}>Add column</Col>
                <Col span={1}>
                    <ButtonGroup className="formview-collapse-button">
                        <Dropdown overlay={this.renderCreateColumnMenu()}>
                            <Button type="dashed" size="small" placement="" onClick={e => {
                                e.stopPropagation()
                            }}>
                                <Avatar className='avatar-add' src='images/icon-core/file-add.svg'/>
                            </Button>
                        </Dropdown>
                    </ButtonGroup>
                </Col>
            </Row>
            <Row>
                <Col span={24}>
                    {this.props.entity.columns == null ? this.renderDsColumns([], null) : this.renderDsColumns(this.props.entity.columns, null)}
                </Col>
            </Row>
        </div>
    }

    renderCreateColumnMenu(row) {
        const {t} = this.props
        return <Menu onClick={this.handleAdd(row)}>
            <Menu.Item key="sse.ScalarType">{t('sse.ScalarType.caption', {ns: 'classes'})}</Menu.Item>
            <Menu.Item key="sse.ArrayType">{t('sse.ArrayType.caption', {ns: 'classes'})}</Menu.Item>
            <Menu.Item key="sse.StructType">{t('sse.StructType.caption', {ns: 'classes'})}</Menu.Item>
        </Menu>
    }

    renderDsColumns(columns, parentKey) {
        const {t} = this.props
        const components = {
            body: {
                row: EditableFormRow,
                cell: EditableCell,
            },
        }

        const columnsHeader = [
            {
                title: t('sse.Column.attrs.columnName.caption', {ns: 'classes'}),
                dataIndex: 'name',
                key: 'name',
                width: '30%',
                onCell: record => ({
                    record,
                    inputType: 'text',
                    dataIndex: 'name',
                    title: t('sse.Column.attrs.columnName.caption', {ns: 'classes'}),
                    editing: this.isEditing(record),
                    handleSave: this.handleSave,
                    entity: this.props.entity,
                    updateEntity: this.props.updateEntity
                })
            },
            {
                title: t('sse.Column.attrs.columnType.caption', {ns: 'classes'}),
                dataIndex: 'type',
                key: 'type',
                width: '15%',
                onCell: record => ({
                    record,
                    inputType: 'text',
                    dataIndex: 'type',
                    title: t('sse.Column.attrs.columnType.caption', {ns: 'classes'}),
                    editing: this.isEditing(record),
                    handleSave: this.handleSave,
                    entity: this.props.entity,
                    updateEntity: this.props.updateEntity
                })
            },
            {
                title: t('sse.ColumnType.attrs.description.caption', {ns: 'classes'}),
                dataIndex: 'colDescription',
                key: 'colDescription',
                width: '40%',
                onCell: record => ({
                    record,
                    inputType: 'text',
                    dataIndex: 'colDescription',
                    title: t('sse.ColumnType.attrs.description.caption', {ns: 'classes'}),
                    editing: this.isEditing(record),
                    handleSave: this.handleSave,
                    entity: this.props.entity,
                    updateEntity: this.props.updateEntity
                })
            },
            {
                title: t('sse.ColumnType.attrs.isNullable.caption', {ns: 'classes'}),
                dataIndex: 'isNullable',
                key: 'isNullable',
                width: '5%',
                render: (isNull) => <Checkbox defaultChecked={isNull} disabled/>,
                onCell: record => ({
                    record,
                    inputType: 'bool',
                    dataIndex: 'isNullable',
                    title: t('sse.ColumnType.attrs.isNullable.caption', {ns: 'classes'}),
                    editing: this.isEditing(record),
                    handleSave: this.handleSave,
                    entity: this.props.entity,
                    updateEntity: this.props.updateEntity
                })
            },
            {
                title: '',
                dataIndex: 'actions',
                key: 'actions',
                width: '10%',
                render: (text, record) => <ButtonGroup className="pull-right">
                    <Tooltip placement="top" title={t("edit")}>
                        <Button type="dashed" size="small" placement="" onClick={(e) => {
                            this.edit(record.key)
                            this.setState({ rowIndex: this.findRowIndex(e.target) })
                            document.addEventListener('click', this.handleClickOutside, true)
                        }}><Icon type="edit"/></Button>
                    </Tooltip>
                    <Tooltip placement="top" title={t("delete")}>
                        <Button type="dashed" size="small" placement="" onClick={() => {
                            Modal.confirm({
                                content: t("confirmdelete"),
                                okText: t("delete"),
                                cancelText: t("cancel"),
                                onOk: () => {
                                    this.handleDelete(record)
                                }
                            })
                        }}><Icon type="delete"/></Button>
                    </Tooltip>
                    {isExpandableType(record.colData) &&
                    <Dropdown overlay={this.renderCreateColumnMenu(record)}>
                        <Button type="dashed" size="small" placement="" onClick={e => {
                            e.stopPropagation()
                        }}>
                            <Avatar className='avatar-add' src='images/icon-core/file-add.svg'/>
                        </Button>
                    </Dropdown>
                    }
                </ButtonGroup>
            }
        ]

        return (
            <div style={{boxSizing: 'border-box', height: '100%', width: '100%'}} className="ag-theme-balham">
                <Table
                    size="small"
                    components={components}
                    pagination={false}
                    columns={columnsHeader}
                    expandedRowRender={column => this.renderSubStruct(column, parentKey)}
                    dataSource={this.getDsColumns(columns, parentKey)}
                    rowClassName={(row) => isExpandableType(row.colData) ? "editable-row" : "editable-row hide-expand-icon"}
                />
            </div>
        )
    }

    handleClickOutside = (e) => {
        const rowIndexClick = this.findRowIndex(e.target)
        const {rowIndex} = this.state
        if (rowIndex && rowIndexClick && rowIndexClick !== rowIndex) {
            this.setState({editingKey: ''})
            document.removeEventListener('click', this.handleClickOutside, true);
        }
    }

    findRowIndex(target){
        let lastElement = target 
        while(lastElement.parentElement){
            if(lastElement.parentElement.rowIndex){
                return lastElement.parentElement.rowIndex
            }else{
                lastElement = lastElement.parentElement
            }
        }
        return null
    }

    isEditing = (record) => {
        return record.key === this.state.editingKey
    }

    edit(key) {
        this.setState({editingKey: key})
    }

    cancel() {
        this.setState({editingKey: ''})
    }

    handleDelete(row) {
        const {entity, updateEntity} = this.props

        const cols = removeByKey(entity.columns, row.name)

        updateEntity({'columns': cols})
    }

    renderSubStruct(column, parentKey) {
        const columnKey = column.key

        if (column.colData.columnType._type_ === "sse.ArrayType") {
            return this.renderStruct(column.colData.columnType.elementType, columnKey)
        } else if (column.colData.columnType._type_ === "sse.StructType") {
            return this.renderStruct(column.colData.columnType, columnKey)
        } else {
            return null
        }
    }

    renderStruct(columnType, parentKey) {
        return this.renderDsColumns(columnType.columns, parentKey)
    }

    getDsColumns(columns, parentKey) {
        return _.map(columns, (c, index) => {
            const col = {}
            col.key = parentKey == null ? index + 1 : `${parentKey}.${index + 1}`
            col.name = c.columnName
            col.colDescription = c.columnType.description
            col.isNullable = c.columnType.isNullable
            col.colData = c
            col.type = displayColumnType(c)
            this.keyCounter = index + 1
            return col
        })
    }

    handleAdd = (row) => {
        return (e) => {
            const {entity, updateEntity} = this.props
            const column = instantiate("sse.Column", {
                columnName: row ? 
                    row.name + "_" + (row.colData.columnType.columns.length + 1)
                    : 
                    "newColumn" + this.newColumnCount++,
                columnType: instantiate(e.key, {})
            })

            return updateEntity({'columns': addByName(entity.columns, column, row ? row.name : undefined)})
        }
    }
}

export default translate()(StructGrid);
