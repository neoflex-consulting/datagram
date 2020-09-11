import React, {Component} from 'react'
import {translate} from "react-i18next"
import {Cascader} from 'antd'
import {getTypeField} from '../../../model.js'
import _ from 'lodash'
import {arrayToColumnType, columnTypeToCascadeArray, findByName} from "./Structure"

class ColumnType extends Component {
    constructor(props) {
        super(props)
        const scalarTypes = getTypeField("sse.ScalarType", "dataType").options
        const scalarTypesUI = scalarTypes.map((t) => {
            return {value: t, label: t}
        })

        const {t} = props

        this.state = {
            scalarTypes: scalarTypes,
            types: [{
                value: "scalar",
                label: t('sse.ScalarType.caption', {ns: 'classes'}),
                children: scalarTypesUI
            }, {
                value: "array",
                label: t('sse.ArrayType.caption', {ns: 'classes'}),
                children: _.concat({
                    value: "struct",
                    label: t('sse.StructType.caption', {ns: 'classes'})
                }, scalarTypesUI)
            }, {
                value: "struct",
                label: t('sse.StructType.caption', {ns: 'classes'})
            }]
        }
    }

    render() {
        return <Cascader key="cc" options={this.state.types} defaultValue={columnTypeToCascadeArray(this.props.value)}
                         onChange={this.onChange}/>
    }

    onChange = (value) => {
        const {entity, updateEntity, record} = this.props

        let col = findByName(entity.columns, record.name)
        if (col !== undefined) {
            let colType = arrayToColumnType(value)
            if (colType !== undefined) {
                col.columnType = colType
                updateEntity({'columns': entity.columns})
            }
        }
    }

}

export default translate()(ColumnType)