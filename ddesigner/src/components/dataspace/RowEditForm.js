import React, {Component} from 'react';
import {translate} from "react-i18next";
import {Form, Input, InputNumber, Checkbox, DatePicker} from 'antd'
import Debounced from '../Debounced'
import moment from 'moment'

function parseJSON(json) {
    try {
        return JSON.parse(json)
    } catch (e) {
        return undefined
    }
}

class RowEditForm extends Component {

    constructor(...args) {
        super(...args);
        this.state = {fields: []}
    }

    isDisabled(column) {
        return this.props.op === "edit" && this.props.ds.primaryKeyCols.includes(column.columnName)
    }

    renderJSONColumn(column) {
        const {row, updateRow} = this.props
        return <Input id={column.columnName} value={JSON.stringify(row[column.columnName])} disabled={this.isDisabled(column)}
                      onChange={e => {
                          updateRow({[column.columnName]: parseJSON(e.target.value)})
                      }}/>
    }

    renderString(column) {
        const {row, updateRow} = this.props
        return <Debounced Component={Input.TextArea} autosize={{minRows: 1, maxRows: 40}} id={column.columnName} disabled={this.isDisabled(column)}
                          value={row[column.columnName]} onChange={e =>
            updateRow({[column.columnName]: e.target.value})
        }/>
    }

    renderNumber(column) {
        const {row, updateRow} = this.props
        const image = row[column.columnName]
        return <InputNumber id={column.columnName} value={image ? Number(image) : 0} disabled={this.isDisabled(column)}
                            onChange={value => {
                                updateRow({[column.columnName]: value ? value.toString() : 0})
                            }}/>
    }

    renderDate(column) {
        const {row, updateRow} = this.props
        return <DatePicker
            disabled={this.isDisabled(column)}
            id={column.columnName}
            value={row[column.columnName] && moment(row[column.columnName])}
            onChange={date => {
                const value = date && date.isValid() ? date.format() : ""
                updateRow({[column.columnName]: value})
            }}
        />
    }

    renderDatetime(column) {
        const {row, updateRow} = this.props
        return <DatePicker
            disabled={this.isDisabled(column)}
            showTime
            format="YYYY-MM-DD HH:mm:ss"
            id={column.columnName}
            value={row[column.columnName] && moment(row[column.columnName])}
            onChange={date => {
                const value = date && date.isValid() ? date.format("YYYY-MM-DDThh:mm:ss") : ""
                updateRow({[column.columnName]: value})
            }}
        />
    }

    renderBoolean(column) {
        const {row, updateRow} = this.props
        return <Checkbox
            disabled={this.isDisabled(column)}
            checked={row[column.columnName] === "true"}
            onChange={e => {
                updateRow({[column.columnName]: e.target.checked.toString()})
            }}
        ></Checkbox>
    }

    renderColumn(column) {
        if (column.columnType && column.columnType._type_ === "sse.ScalarType") {
            if (["STRING", "BINARY"].includes(column.columnType.dataType)) return this.renderString(column)
            if (["DECIMAL", "INTEGER", "LONG", "FLOAT", "DOUBLE"].includes(column.columnType.dataType)) return this.renderNumber(column)
            if (["DATE"].includes(column.columnType.dataType)) return this.renderDate(column)
            if (["TIME", "DATETIME"].includes(column.columnType.dataType)) return this.renderDatetime(column)
            if (["BOOLEAN"].includes(column.columnType.dataType)) return this.renderBoolean(column)
        }
        return this.renderJSONColumn(column)
    }

    render() {
        const {ds} = this.props
        return (
            <div style={{height: '60vh', overflow: 'auto'}}><Form onSubmit={e => e.preventDefault()} layout={"vertical"}>
                {(ds.columns || []).map(column=>{
                    return <Form.Item key={column.columnName} label={column.columnName}>
                        {this.renderColumn(column)}
                    </Form.Item>
            })}
            </Form></div>
        )
    }
}

export default translate()(RowEditForm);
