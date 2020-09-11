import data from './model.json'
import LinkedClasses from './models/LinkedClasses'
import etl_Transformation from './models/etl_Transformation.json'
import etl_Workflow from './models/etl_Workflow.json'
import etl_DeltaSource from './models/etl_DeltaSource.json'
import etl_DeltaTarget from './models/etl_DeltaTarget.json'
import auth_AuditInfo from './models/auth_AuditInfo'
import rt_WorkflowDeployment from './models/rt_WorkflowDeployment.json'
import rt_TransformationDeployment from './models/rt_TransformationDeployment.json'
import etl_SlaDefinition from './models/etl_SlaDefinition.json'
import sse_AbstractDataset from './models/sse_AbstractDataset.json'
import sse_AbstractNotebook from './models/sse_AbstractNotebook.json'
import sse_Workspace from './models/sse_Workspace'
import sse_LibraryNotebook from './models/sse_LibraryNotebook'
import rt_Export from './models/rt_Export'
import rt_Atlas from './models/rt_Atlas'
import rt_Airflow from './models/rt_Airflow'
import rt_MLFlowServer from './models/rt_MLFlowServer'
import sm_SMInstance from './models/sm_SMInstance'
import evs_EventsProcessor from './models/evs_EventsProcessor'
import _ from "lodash";
import moment from 'moment'
import {registerEvents} from './events'


const fullJavaClassName = {
    'STRING': 'java.lang.String',
    'BOOLEAN': 'java.lang.Boolean',
    'DECIMAL': 'java.math.BigDecimal',
    'INTEGER': 'java.lang.Integer',
    'LONG': 'java.lang.Long',
    'FLOAT': 'java.lang.Float',
    'DOUBLE': 'java.lang.Double',
    'BINARY': 'Array[Byte]',
    'DATE': 'java.sql.Date',
    'DATETIME': 'java.sql.Timestamp',
    'TIME': 'java.sql.Timestamp',
    'STRUCT': 'java.lang.String',
    'ARRAY': 'Array[java.lang.String]',
}

function isObject(item) {
    return (item && typeof item === 'object' && !Array.isArray(item));
}
function mergeDeep(target, ...sources) {
    if (!sources.length) return target;
    const source = sources.shift();

    if (isObject(target) && isObject(source)) {
        for (const key in source) {
            if (isObject(source[key])) {
                if (!target[key]) Object.assign(target, { [key]: {} });
                mergeDeep(target[key], source[key]);
            } else {
                Object.assign(target, { [key]: source[key] });
            }
        }
    }

    return mergeDeep(target, ...sources);
}

function getDefaultValue(field) {
    if (!field.defaultValue) {
        return undefined
    }
    if (field.type === "number") {
        return parseFloat(field.defaultValue)
    }
    if (field.type === "boolean") {
        return field.defaultValue.toLowerCase() === "true"
    }
    if (field.type.toLowerCase().startsWith("date")) {
        return moment(field.defaultValue).toDate()
    }
    return field.defaultValue
}

function transformField(f) {
    if (["line", "set"].includes(f.type)) return {...f, fields: f.fields.map(inner=>transformField(inner))}
    return {
        ...f,
        isReference: ["select", "multi", "form", "table"].includes(f.type),
        isAttribute: !["line", "set", "select", "multi", "form", "table"].includes(f.type),
        isContained: ["form", "table"].includes(f.type),
        isNotContained: ["select", "multi"].includes(f.type),
        isArray: ["multi", "table", "multiString", "stringList"].includes(f.type),
        defaultValue: getDefaultValue(f)
    }
}

var model = mergeDeep({}, data, LinkedClasses, etl_Transformation, etl_Workflow, auth_AuditInfo,
    rt_WorkflowDeployment, rt_TransformationDeployment, etl_SlaDefinition, rt_Export,
    sse_AbstractDataset, sse_Workspace, evs_EventsProcessor, rt_Atlas, sse_AbstractNotebook, rt_MLFlowServer,
    sm_SMInstance, sse_LibraryNotebook, rt_Airflow, etl_DeltaSource, etl_DeltaTarget)
model.eClasses = _.mapValues(model.eClasses, (eClass, name)=>{
    return _.mapValues(eClass, (v, k)=> {
        return k==="fields" ? v.map(f=> transformField(f)) : v
    })
})
registerEvents(model)

var getClassDef = (typeName) => {
    return model.eClasses[typeName]
}

function getEntityClassFeature(model, typeName, entity, feature) {
    typeName = typeName || entity._type_
    const value = model.eClasses[typeName][feature]
    return _.isFunction(value) ? value(entity) : value
}

function getTypeFields(typeName) {
    return getClassDef(typeName).fields
}

function flatGetTypeFields(typeName) {
    return getClassDef(typeName).fields.flatMap(f=>{
        if (["line", "set"].includes(f.type)) return f.fields
        return [f]
    })
}


var getTypeField = (typeName, fieldName) => {
    return getTypeFields(typeName).find(f=>f.name===fieldName)
}

var getModel = ()=>model

export {getClassDef, fullJavaClassName, getModel, mergeDeep, getTypeFields, getTypeField, getEntityClassFeature, transformField, flatGetTypeFields}
