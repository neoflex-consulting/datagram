import {
    addByKey,
    columnTypeToCascadeArray,
    displayColumnType,
    findByKey,
    isExpandableType,
    removeByKey,
    updateByKey
} from '../components/dataspace/structure/Structure'

const regular = {
    columnName: "regularColumn", columnType: {
        _type_: "sse.ScalarType",
        dataType: "STRING"
    }
}

const simpleArray = {
    columnName: "simpleArray", columnType: {
        _type_: "sse.ArrayType",
        elementType: {
            _type_: "sse.ScalarType",
            dataType: "STRING"
        }
    }
}

const nestedA = {
    columnName: "nestedA",
    columnType: {
        _type_: "sse.ScalarType",
        dataType: "STRING"
    }
}

const nestedB = {
    columnName: "nestedB",
    columnType: {
        _type_: "sse.ScalarType",
        dataType: "NUMBER"
    }
}

const nestedStruct = {
    columnName: "nestedStruct", columnType: {
        _type_: "sse.ArrayType",
        elementType: {
            _type_: "sse.StructType",
            columns: [nestedA, nestedB]
        }
    }
}

it('Test columnType UI representation', () => {
    expect(displayColumnType(regular)).toBe('STRING')
    expect(isExpandableType(regular)).toBe(false)

    expect(displayColumnType(simpleArray)).toBe('array<STRING>')
    expect(isExpandableType(simpleArray)).toBe(false)

    expect(displayColumnType(nestedStruct)).toBe('array<struct<nestedA: STRING,nestedB: NUMBER>>')
    expect(isExpandableType(nestedStruct)).toBe(true)
})

it('Test column remove', () => {
    const columns = [regular, simpleArray, nestedStruct]

    expect(findByKey(columns, regular.columnName)).toBe(regular)
    expect(findByKey(columns, nestedStruct.columnName + '.' + nestedA.columnName)).toBe(nestedA)

    expect(removeByKey(columns, regular.columnName)).toEqual([simpleArray, nestedStruct])
    expect(removeByKey(columns, simpleArray.columnName)).toEqual([regular, nestedStruct])
    expect(removeByKey(columns, nestedStruct.columnName + '.' + nestedA.columnName)).toEqual([regular, simpleArray,
        {
            columnName: "nestedStruct", columnType: {
                _type_: "sse.ArrayType",
                elementType: {
                    _type_: "sse.StructType",
                    columns: [nestedB]
                }
            }
        }])
})

it('Test column update', () => {
    const columns = [regular, simpleArray, nestedStruct]

    const updatedColumns = updateByKey(columns, regular.columnName, {description: "testDescription"})

    expect(findByKey(updatedColumns, regular.columnName).description).toBe("testDescription")
})

it('Test Cascadeur interaction', () => {
    expect(columnTypeToCascadeArray('STRING')).toEqual(['scalar', 'STRING'])
    expect(columnTypeToCascadeArray('array<STRING>')).toEqual(['array', 'STRING'])
    expect(columnTypeToCascadeArray('array<struct<fieldA: STRING>>')).toEqual(['array', 'struct'])
})

it('Test column add', () => {
    const columns = [simpleArray, nestedStruct]
    const afterAdd = addByKey(columns, regular)

    expect(afterAdd).toEqual([simpleArray, nestedStruct, regular])

    const afterAddNested = addByKey(columns, regular, nestedStruct.columnName)
    expect(findByKey(afterAddNested, nestedStruct.columnName + '.' + regular.columnName)).toBe(regular)
})