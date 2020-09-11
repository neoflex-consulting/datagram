import data from './designer/designer.json';

function getTransientElements(value, ready) {
    if(!ready) {
        ready = []
    }
    if(!value || typeof value !== "object" || ready.indexOf(value) !== -1) {
        return []
    }
    var result = []
    if(!Array.isArray(value)) {
        if(value.transient){
            result.push(value)
        }
        if(ready.indexOf(value) === -1) {
            ready.push(value)
            Object.entries(value).forEach(e=>{
                result.push.apply(result, getTransientElements(e[1], ready));
            })
        }
    } else {
        if(ready.indexOf(value) === -1) {
            value.forEach(node=>{
                result.push.apply(result, getTransientElements(node, ready))
            })
        }
    }
    return result
}

var classExtension ={
    nodes: function(entity){
        if(entity._type_ === "etl.Workflow") {
            return entity.nodes || []
        }
        const groups = [];
        var result = [];
        if(this[entity._type_]){
            this[entity._type_].nodesDef.forEach((def)=>{
                if(!groups.includes(def.group)){
                    groups.push(def.group)
                }
            });

            groups.forEach(g=>{
                if(entity[g]){
                    result = result.concat(entity[g])
                }
            });
        }
        return result;
    },
    findPortByPortId: function(entityType, step, portId) {
        var def = classExtension[entityType].nodesDef.find(n=>n._type_ === step._type_)
        var portDef = def.ports.find(p=>p.id === portId)
        if(!portDef) {
            let foundPort
            def.ports.filter(p=>p.multiple).forEach(p=>{
                foundPort = foundPort || step[p.attribute][portId]
            })
            return foundPort
        } else {
            return step[portDef.attribute]
        }
    },
    "etl.Transformation": {
        transitions: function(entity){
            return entity.transitions
        },
        nodesDef: data.transformationSteps
    },
    "etl.Workflow": {
        nodesDef: data.workflowNodes
    },
    all: function() {
        return data.transformationSteps.concat(data.workflowNodes)
    }
}

export { classExtension, getTransientElements }
