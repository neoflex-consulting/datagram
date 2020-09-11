import _ from 'lodash'
import {getTypeField} from "../../../model"
import {instantiate} from "../../../utils/meta"

function isExpandableType(column) {
    const isScalarType = column.columnType._type_ === "sse.ScalarType"
    if (column.columnType._type_ === "sse.ArrayType") {
        const arrayType = column.columnType.elementType
        if (arrayType == null) {
            return false
        }
        const isSimpleArrayType = arrayType._type_ === "sse.ScalarType"
        return !isSimpleArrayType
    }
    return !isScalarType
}

function displayColumnType(column) {
    if (column == null || column.columnType == null) {
        return ""
    }

    switch (column.columnType._type_) {
        case "sse.ScalarType":
            return column.columnType.dataType
        case "sse.ArrayType":
            return "array<" + displayColumnType({
                columnName: "array",
                columnType: column.columnType.elementType
            }) + ">"
        case "sse.StructType":
            return "struct<" + _.join(_.map(column.columnType.columns, (c) => {
                return c.columnName + ": " + displayColumnType(c)
            })) + ">"
        default:
            return ""
    }
}

function findByName(columns, name) {
    //Sorry the hack.
    let foundColumn
    _.cloneDeepWith(columns, (a)=>{
        if(a && a.columnName && a.columnName === name){
            foundColumn = a  
        }
    })
    return foundColumn
}

function addByName(columns, newColumn, name) {
    if (name !== undefined) {
        let colToAdd = findByName(columns, name)
        if (colToAdd !== undefined && isExpandableType(colToAdd)) {
            if (colToAdd.columnType._type_ === "sse.ArrayType") {
                colToAdd.columnType.elementType.columns = _.concat(colToAdd.columnType.elementType.columns, newColumn)
            } else if (colToAdd.columnType._type_ === "sse.StructType") {
                colToAdd.columnType.columns = _.concat(colToAdd.columnType.columns, newColumn)
            }
        }
        return columns
    }
    return _.concat(columns, newColumn)
}

function removeByKey(columns, key) {
    const parts = _.split(key, '.')

    const colToFind = _.head(parts)
    const col = _.find(columns, (c) => {
        return c.columnName === colToFind
    })

    if (parts.length === 1) {
        return _.filter(columns, (c) => {
            return c.columnName !== colToFind
        })
    } else if (isExpandableType(col)) {
        if (col.columnType._type_ === "sse.ArrayType") {
            col.columnType.elementType.columns = removeByKey(col.columnType.elementType.columns, _.join(_.tail(parts), '.'))
        } else if (col.columnType._type_ === "sse.StructType") {
            col.columnType.columns = removeByKey(col.columnType.columns, _.join(_.tail(parts), '.'))
        }
    }

    return columns
}

function columnTypeToCascadeArray(typeRepr) {
    const scalarTypes = getTypeField("sse.ScalarType", "dataType").options

    if (_.indexOf(scalarTypes, typeRepr) >= 0) {
        return ["scalar", typeRepr]
    } else {
        const parts = _.split(typeRepr, /[<>]/)
        if (parts.length >= 2) {
            return [parts[0], parts[1]]
        }
    }
}

function arrayToColumnType(arrValue) {
    const [type, subType] = arrValue

    switch (type) {
        case "scalar":
            return instantiate("sse.ScalarType", {dataType: subType})
        case "struct":
            return instantiate("sse.StructType")
        case "array":
            return instantiate("sse.ArrayType",
                {
                    elementType: subType === "struct" ? instantiate("sse.StructType") :
                        instantiate("sse.ScalarType", {dataType: subType})
                })
        default:
            return undefined
    }
}

export {
    isExpandableType,
    displayColumnType,
    findByName,
    removeByKey,
    columnTypeToCascadeArray,
    addByName,
    arrayToColumnType
}