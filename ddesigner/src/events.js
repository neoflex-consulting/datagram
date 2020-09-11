
const events = [
    {_type_: "etl.Transformation", field: "name", property: "onChange", value: (updates, value, field, entity) => {
            if ((entity.name || "") === (entity.label || "")) {
                updates.label = value
            }
        }
    },
    {_type_: "etl.Workflow", field: "name", property: "onChange", value: (updates, value, field, entity) => {
            if ((entity.name || "") === (entity.label || "")) {
                updates.label = value
            }
        }
    },
    {_type_: "sse.Workspace", field: "name", property: "readOnly", value: true},
    {_type_: "sse.Workspace", field: "shortName", property: "onChange", value: (updates, value, field, entity) => {
            let name = value
            if (entity.parent) {
                name = entity.parent.name + "_" + name
            }
            updates.name = name
        }
    },
    {_type_: "sse.AbstractDataset", field: "name", property: "readOnly", value: true},
    {_type_: "sse.AbstractDataset", field: "shortName", property: "onChange", value: (updates, value, field, entity) => {
            let name = value
            if (entity.workspace) {
                name = entity.workspace.name + "_" + name
            }
            updates.name = name
        }
    },
    {_type_: "sse.Notebook", field: "name", property: "readOnly", value: true},
    {_type_: "sse.Notebook", field: "shortName", property: "onChange", value: (updates, value, field, entity) => {
            let name = value
            if (entity.workspace) {
                name = entity.workspace.name + "_" + name
            }
            updates.name = name
        }
    },
]

function isInstanceOf(_type_, eClass) {
    return (eClass.successors && eClass.successors[0] === _type_) || (eClass.ancestors && eClass.ancestors.includes(_type_))
}

function setProperty(fields, name, property, value) {
    fields.forEach(f=>{
        if (["line", "set"].includes(f.type)) setProperty(f.fields, name, property, value)
        else if (f.name === name) {
            f[property] = value
        }
    })
}

function registerEvents(model) {
    events.forEach((event)=>{
        Object.values(model.eClasses).filter(eClass=>isInstanceOf(event._type_, eClass)).forEach(eClass=>{
            setProperty(eClass.fields || [], event.field, event.property, event.value)
        })
    })
}

export {registerEvents}
