import _ from 'lodash'

const nodeOutputFieldsFactory = {
    "etl.TableTarget": (node) => {
        return node.inputPort.fields.map(f => copyField(f))
    },
    "etl.Selection": (node) => {
        return node.inputPort.fields.map(f => copyField(f))
    },
    "etl.Sort": (node) => {
        return node.inputPort.fields.map(f => copyField(f))
    },
    "etl.Sequence": (node) => {
        return [...node.inputPort.fields.map(f => copyField(f)), {
            _type_: "dataset.Field",
            name: node.fieldName || "",
            dataTypeDomain: "DECIMAL"
        }]
    },
    "etl.ModelBasedAnalysis": (node) => {
        return [...node.inputPort.fields.map(f => copyField(f)), {
            _type_: "dataset.Field",
            name: node.labelFieldName || "",
            dataTypeDomain: "DECIMAL"
        }]
    },
    "etl.ExplodeStep": (node) => {
        return [
            ...node.inputPort.fields.map(f => copyField(f)),
            ...node.explodeFields.map(e => {
                return {
                    _type_: "dataset.Field",
                    name: e.alias,
                    dataTypeDomain: e.dataTypeDomain,
                    domainStructure: copyFieldType(e.domainStructure)
                }
            })
        ].filter(f=>!!f)
    },
    "etl.Aggregation": (node) => {
        const getAggType = (dataTypeDomain, aggregationFunction) => {
            if (["LIST"].includes(aggregationFunction)) return "STRING"
            if (['FIRST', 'LAST', 'MIN', 'MAX'].includes(aggregationFunction)) return dataTypeDomain
            return 'DECIMAL'
        }
        const groupByFields = node.groupByFieldName.map(n => node.inputPort.fields.find(f => f.name === n)).filter(f => !!f).map(f => copyField(f))
        const pivotFields = !node.pivotField ? [] : _.flatten(node.pivotParameters.map(p => node.aggregationParameters.map(a => {
            const inputField = node.inputPort.fields.find(f => f.name === a.fieldName)
            if (!inputField) return undefined
            const dataTypeDomain = getAggType(inputField.dataTypeDomain, a.aggregationFunction)
            const name = p.resultFieldName + "_" + (a.resultFieldName ? a.resultFieldName : a.fieldName)
            return {_type_: "dataset.Field", name, dataTypeDomain}
        }))).filter(f => !!f)
        const aggFields = node.pivotField ? [] : node.aggregationParameters.map(a => {
            const inputField = node.inputPort.fields.find(f => f.name === a.fieldName)
            if (!inputField) return undefined
            const dataTypeDomain = getAggType(inputField.dataTypeDomain, a.aggregationFunction)
            const name = a.resultFieldName ? a.resultFieldName : a.fieldName
            return {_type_: "dataset.Field", name, dataTypeDomain}
        }).filter(f => !!f)
        return [...groupByFields, ...pivotFields, ...aggFields]
    },
}

function correctOutputFields(node) {
    const factory = nodeOutputFieldsFactory[node._type_]
    if (factory) {
        const fields = factory(node)
        if (!sameFieldList(fields, node.outputPort.fields)) {
            node.outputPort.fields = fields
            return true
        }
    }
    return false
}

function diffPorts(newFields, oldFields) {
    const deleted = oldFields.filter(of => newFields.every(nf => of.name.toLowerCase() !== nf.name.toLowerCase())).map(f => {
        return {oldField: f, newField: undefined}
    })
    const added = newFields.map((nf, index) => {
        let of = oldFields.find(of => of.name.toLowerCase() === nf.name.toLowerCase())
        return {oldField: of, newField: nf}
    }).filter(item => !item.oldField || item.oldField.dataTypeDomain !== item.newField.dataTypeDomain)
    if (added.length === 1 && deleted.length === 1) {
        added[0].oldField = deleted[0].oldField
        deleted.length = 0
    }
    return [...deleted, ...added]
}

function targetInputPortHasChanged(transformation, target, inputPort, oldFields) {
    console.log("targetInputPortHasChanged");
    const mappingType = target._type_ === "etl.StoredProcedureTarget" ? "etl.StoredProcedureParamFeature" : (
        ["etl.HBaseTarget", "etl.StreamTarget"].includes(target._type_) ? "etl.HBaseTargetFeature" : "etl.TableTargetFeature"
    )
    const nameAttr = mappingType === "etl.StoredProcedureParamFeature" ? "paramName" : (
        mappingType === "etl.HBaseTargetFeature" ? "column" : "targetColumnName"
    )
    target.inputFieldsMapping = [];
    inputPort.fields.forEach(f=>{
                console.log("Adding field: " + f.name);
                target.inputFieldsMapping.push({
                       _type_: mappingType,
                       inputFieldName: f.name,
                       [nameAttr]: f.name
                });
    });

    /*if(true){
        return;
    }


    target.inputFieldsMapping = target.inputFieldsMapping || []
    diffPorts(inputPort.fields, oldFields).forEach(diff => {
        console.log(diff);
        const {oldField, newField} = diff
        var index = inputPort.fields.indexOf(newField);
        if(index === -1){
            index = target.inputFieldsMapping.length-1;
        }
        console.log("INDEX: " + index);
        if (oldField) {
            if (newField) {
                const mapping = target.inputFieldsMapping.find(m => m.inputFieldName === oldField.name)
                if (mapping) {
                    mapping.inputFieldName = newField.name
                    mapping[nameAttr] = newField.name
                }
            } else {
                target.inputFieldsMapping = target.inputFieldsMapping.filter(m => m.inputFieldName.toLowerCase() !== oldField.name.toLowerCase())
            }
        } else {
            if(mappingType === "etl.TableTargetFeature"){
                const mapping = target.inputFieldsMapping.find(m => m.targetColumnName.toLowerCase() === newField.name.toLowerCase())
                if(mapping){
                    mapping.inputFieldName = newField.name
                }else{
                    target.inputFieldsMapping.push({
                       _type_: mappingType,
                       inputFieldName: newField.name,
                       [nameAttr]: newField.name
                    })
                    console.log("Spliced into"  + index + ", " + newField.name);
                }
            }else{
                target.inputFieldsMapping.push({
                    _type_: mappingType,
                    inputFieldName: newField.name,
                    [nameAttr]: newField.name
                })
                console.log("Spliced 2 into"  + index + ", " + newField.name);
            }
        }
    })*/

    if(mappingType === "etl.TableTargetFeature"){
        target.inputFieldsMapping.forEach(m=>{
            if(!m.inputFieldName || m.inputFieldName === ""){
                m.inputFieldName = "";
            }
        });
    }
}

function stepInputPortHasChanged(transformation, transformationStep, inputPort, oldFields) {
    if (correctOutputFields(transformationStep)) { // fixed output structure
        outputPortHasChanged(transformation, transformationStep.outputPort)
        return
    }
    const fieldType = ["etl.Projection", "etl.Join"].includes(transformationStep._type_) ? "etl.ProjectionField" :
        (transformationStep._type_ === "etl.Union" ? "etl.UnionField" : "dataset.Field")
    diffPorts(inputPort.fields, oldFields).forEach(diff => {
        const {oldField, newField} = diff
        if (oldField) {
            if (newField) {
                const field = transformationStep.outputPort.fields.find(f => f.name === oldField.name)
                if (field) {
                    field.name = newField.name
                    field.dataTypeDomain = newField.dataTypeDomain
                    if (field._type_ === "etl.ProjectionField") {
                        field.sourceFields = field.sourceFields.filter(sf => sf !== oldField)
                        field.sourceFields.push(newField)
                    } else if (field._type_ === "etl.UnionField") {
                        if (field.inputPortField && field.inputPortField.name === oldField.name) {
                            field.inputPortField = newField
                        }
                    }
                }
            } else {
                transformationStep.outputPort.fields.forEach(field => {
                    if (field.name === oldField.name) {
                        if (field._type_ === "etl.ProjectionField") {
                            field.sourceFields = field.sourceFields.filter(sf => sf !== oldField)
                            if (field.sourceFields.length === 0) field.toDelete = true
                        }
                        else if (field._type_ === "etl.UnionField") {
                            if (field.inputPortField && field.inputPortField.name === oldField.name) {
                                field.inputPortField = undefined
                            }
                            if (!field.inputPortField && !field.unionPortField) field.toDelete = true
                        }
                    }
                })
                transformationStep.outputPort.fields = transformationStep.outputPort.fields.filter(field => field.toDelete !== true)
            }
        } else {
            const field = transformationStep.outputPort.fields.find(f => f.name === newField.name)
            if (field) {
                if (fieldType === "etl.ProjectionField") {
                    if (field.sourceFields.length == 0) {
                        field.sourceFields.push(newField)
                    }
                }
                if (fieldType === "etl.UnionField") {
                    field.inputPortField = newField
                }
            }
            else {
                const field = {_type_: fieldType, name: newField.name, dataTypeDomain: newField.dataTypeDomain}
                if (fieldType === "etl.ProjectionField") {
                    field.sourceFields = [newField]
                    field.fieldOperationType = "ADD"
                }
                if (fieldType === "etl.UnionField") {
                    field.inputPortField = newField
                }
                transformationStep.outputPort.fields.push(field)
            }
        }
    })
    outputPortHasChanged(transformation, transformationStep.outputPort)
}

function stepJoineePortHasChanged(transformation, transformationStep, inputPort, oldFields) {
    const fieldType = "etl.ProjectionField"
    diffPorts(inputPort.fields, oldFields).forEach(diff => {
        const {oldField, newField} = diff
        if (oldField) {
            if (newField) {
                const field = transformationStep.outputPort.fields.find(f => f.name === oldField.name)
                if (field) {
                    field.name = newField.name
                    field.dataTypeDomain = newField.dataTypeDomain
                    field.sourceFields = field.sourceFields.filter(sf => sf !== oldField)
                    field.sourceFields.push(newField)
                }
            } else {
                transformationStep.outputPort.fields.forEach(field => {
                    if (field.name === oldField.name) {
                        field.sourceFields = field.sourceFields.filter(sf => sf !== oldField)
                        if (field.sourceFields.length === 0) field.toDelete = true
                    }
                })
                transformationStep.outputPort.fields = transformationStep.outputPort.fields.filter(field => field.toDelete !== true)
            }
        } else {
            const field = transformationStep.outputPort.fields.find(f => f.name === newField.name)
            if (field) {
                if (field.sourceFields.length == 0) {
                    field.sourceFields.push(newField)
                }
            }
            else {
                transformationStep.outputPort.fields.push({
                    _type_: fieldType,
                    name: newField.name,
                    dataTypeDomain: newField.dataTypeDomain,
                    sourceFields: [newField]
                })
            }
        }
    })
    outputPortHasChanged(transformation, transformationStep.outputPort)
}

function stepUnionPortHasChanged(transformation, transformationStep, inputPort, oldFields) {
    const fieldType = "etl.UnionField"
    diffPorts(inputPort.fields, oldFields).forEach(diff => {
        const {oldField, newField} = diff
        if (oldField) {
            if (newField) {
                const field = transformationStep.outputPort.fields.find(f => f.name === oldField.name)
                if (field) {
                    field.name = newField.name
                    field.dataTypeDomain = newField.dataTypeDomain
                    if (field.unionPortField && field.unionPortField.name === oldField.name) {
                        field.unionPortField = newField
                    }
                }
            } else {
                transformationStep.outputPort.fields = transformationStep.outputPort.fields.filter(field => {
                    if (field.name !== oldField.name) return true
                    if (field.unionPortField && field.unionPortField.name === oldField.name) {
                        field.unionPortField = undefined
                    }
                    return !!field.inputPortField || !!field.unionPortField
                })
            }
        } else {
            const field = transformationStep.outputPort.fields.find(f => f.name === newField.name)
            if (field) {
                field.unionPortField = newField
            }
            else {
                const field = {
                    _type_: fieldType,
                    name: newField.name,
                    dataTypeDomain: newField.dataTypeDomain,
                    unionPortField: newField
                }
                transformationStep.outputPort.fields.push(field)
            }
        }
    })
    outputPortHasChanged(transformation, transformationStep.outputPort)
}

function stepSQLPortHasChanged(transformation, transformationStep, inputPort, oldFields) {
}

function inputPortHasChanged(transformation, inputPort, oldFields) {
    for (let target of transformation.targets) {
        if (target.inputPort === inputPort) {
            targetInputPortHasChanged(transformation, target, inputPort, oldFields)
            return
        }
    }
    for (let transformationStep of transformation.transformationSteps) {
        if (transformationStep.inputPort === inputPort) {
            stepInputPortHasChanged(transformation, transformationStep, inputPort, oldFields)
            return
        }
        if (transformationStep.joineePort === inputPort) {
            stepJoineePortHasChanged(transformation, transformationStep, inputPort, oldFields)
            return
        }
        if (transformationStep.unionPort === inputPort) {
            stepUnionPortHasChanged(transformation, transformationStep, inputPort, oldFields)
            return
        }
        if (transformationStep.sqlPorts && transformationStep.sqlPorts.includes(inputPort)) {
            stepSQLPortHasChanged(transformation, transformationStep, inputPort, oldFields)
            return
        }
    }
}

function sameFields(f1, f2) {
    return !!f1 === !!f2 && f1.name.toLowerCase() === f2.name.toLowerCase() && f1.dataTypeDomain === f2.dataTypeDomain
}

function deepCloneWithoutId(object) {
    return _.cloneDeepWith(object, value=>{
        if (_.isPlainObject(value) && !!value['e_id']) {
            return deepCloneWithoutId(_.omit(value, ['e_id']))
        }
    })
}

function copyFieldType(fieldType) {
    if (!fieldType) return fieldType
    return deepCloneWithoutId(fieldType)
}

function copyField(f) {
    return {
        _type_: "dataset.Field",
        name: f.name,
        dataTypeDomain: f.dataTypeDomain,
        domainStructure: copyFieldType(f.domainStructure)
    }
}

function outputPortHasChanged(transformation, outPutPort) {
    for (let transition of transformation.transitions) {
        if (transition.start === outPutPort) {
            normalizeTransition(transformation, transition)
            return
        }
    }
}

function sameFieldList(left, right) {
    return left && right && left.length === right.length && left.every((l, i) => sameFields(l, right[i]))
}

function normalizeTransition(transformation, transition) {
    const left = transition.start.fields || []
    const right = transition.finish.fields || []
    if (!sameFieldList(left, right)) {
        const oldFields = [...right]
        transition.finish.fields = transition.start.fields.map((f, i) => sameFields(f, right[i]) ? right[i] : copyField(f))
        inputPortHasChanged(transformation, transition.finish, oldFields)
    }
}

function getStepInputPorts(s) {
    const inputPorts = []
    if (s.inputPort) {
        inputPorts.push(s.inputPort)
    }
    if (s.joineePort) {
        inputPorts.push(s.joineePort)
    }
    if (s.unionPort) {
        inputPorts.push(s.unionPort)
    }
    if (s.sqlPorts) {
        inputPorts.push(...s.sqlPorts)
    }
    return inputPorts
}

function getAllOutputSteps(transformation) {
    return [...transformation.transformationSteps, ...transformation.targets]
}

function getAllInputPorts(transformation) {
    return _.flatMap(getAllOutputSteps(transformation), s => getStepInputPorts(s))
}

function checkLostInputPorts(transformation) {
    getAllInputPorts(transformation).forEach(inputPort => {
        if (inputPort.fields && inputPort.fields.length > 0) {
            const t = transformation.transitions.find(t => t.finish === inputPort)
            if (!t) {
                const oldFields = inputPort.fields
                inputPort.fields = []
                inputPortHasChanged(transformation, inputPort, oldFields)
            }
        }
    })
}

function normalizeTransformationPorts(transformation, old) {
    transformation.transitions = transformation.transitions.filter(t=>t.start && t.finish)
    transformation.transformationSteps.forEach(node => {
        correctOutputFields(node);
    })
    transformation.transitions.forEach(t => {
        normalizeTransition(transformation, t)
    })
    checkLostInputPorts(transformation)
    return transformation
}

export {normalizeTransformationPorts, copyField, deepCloneWithoutId}
