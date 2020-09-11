import {getClassDef, getTypeFields, flatGetTypeFields} from '../model';
import _ from 'lodash'
import resource from "../Resource";

const getBasename = (pathname) => {
    const part = 'index.html'
    const index = pathname.indexOf(part)
    if (index >= 0) {
        return pathname.substr(0, index/* + part.length*/)
    }
    else {
        return '/'
    }
}

function getIcon(m) {
    let icon = m.icon
    if (!icon) {
        var type = m._type_ === "ecore.EClass" ? m.name : m._type_
        if (type) {
            icon = _.get(getClassDef(type), 'icon', "arrow-right.svg")
        }
    }
    return ("images/icon-core/" + icon)
}


function uuidv4() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        var r = Math.random() * 16 | 0, v = c === 'x' ? r : ((r & 0x3) | 0x8);
        return v.toString(16);
    });
}

function instantiate(typeName, props) {
    const entity = {_type_: typeName, name: '', ...(props || {})}
    getTypeFields(typeName).forEach(f=>{
        if (f.isArray) {
            entity[f.name] = []
        }
        else {
            if (f.isUUID) {
                entity[f.name] = uuidv4()
            }
            if (f.defaultValue !== undefined) {
                entity[f.name] = f.defaultValue
            }
        }
    })
    return entity
}

function walkObject(object, visitor, state) {
    const callVisitor = (field, index, value) => {
        const localState = _.clone(state)
        const after = visitor(field, index, value, localState)
        if (field.isContained && value) {
            walkObject(value, visitor, localState)
            if (after) {
                after(field, index, value, localState)
            }
        }
    }
    getTypeFields(object._type_).forEach(field=>{
        if (field.isReference) {
            const value = object[field.name]
            if (field.isArray && value && _.isArray(value)) {
                //callVisitor(field, -1, value)
                value.forEach((value2, index)=>{
                    callVisitor(field, index, value2)
                })
            }
            else {
                callVisitor(field, -1, value)
            }
        }
    })
    return state
}

function getPaths(obj, paths, prefix) {
    return walkObject(obj, (field, index, value, state) => {
        const {prefix, paths} = state
        if (field.isContained && value && (!field.isArray || index >= 0)) {
            const path = field.isArray ? prefix + field.name + '[' + index + ']' : prefix + field.name
            state.prefix = path + "."
            paths.set(value, path)
        }
    }, {paths, prefix: ""}).paths
}

function mapIdToObject(obj, id2obj) {
    return walkObject(obj, (field, index, value, state) => {
        const {id2obj} = state
        if (field.isContained && value && (!field.isArray || index >= 0) && value.e_id) {
            id2obj.set(value.e_id, value)
        }
    }, {id2obj}).id2obj
}

function findNonContainedReferences(obj, prefix, paths, refs) {
    return walkObject(obj, (field, index, value, state) => {
        const {prefix, paths, refs} = state
        if (!field.isArray || index >= 0) {
            const path = field.isArray ? prefix + field.name + '[' + index + ']' : prefix + field.name
            state.prefix = path + "."
            if (!field.isContained) {
                const refto = value ? paths.get(value) : undefined
                refs.push({path, refto})
            }
        }
    }, {prefix, paths, refs}).refs
}

function isEntityEquel(obj, other) {
    if (!!obj !== !!other) {
        return false
    }
    if (!obj || !other || obj === other) {
        return true
    }
    if (obj._type_ !== other._type_ || obj.e_id !== other.e_id) {
        return false
    }
    function isFieldEqual(field, obj, other) {
        if (field.isContained) {
            return isEntityEquel(obj, other)
        }
        else {
            if (!!obj !== !!other) {
                return false
            }
            if (!obj || !other || obj === other) {
                return true
            }
            return obj._type_ === other._type_ && obj.e_id === other.e_id
        }
    }
    return flatGetTypeFields(obj._type_).every(field=>{
        const value = obj[field.name]
        const value2 = other[field.name]
        if (field.isReference) {
            if (field.isArray && value && _.isArray(value)) {
                return value.every((elt, index)=>{
                    return isFieldEqual(field, elt, value2[index])
                })
            }
            else {
                return isFieldEqual(field, value, value2)
            }
        }
        else {
            return _.isEqual(value, value2)
        }
    })
}

function clone(entity, deep = true) {
    const cloned = {_type_: entity._type_}
    getTypeFields(entity._type_).forEach(f=>{
        const value = entity[f.name]
        if (value !== undefined) {
            if (f.isReference) {
                if (deep) {
                    if (f.isArray) {
                        cloned[f.name] = value.map(e=>clone(e, f.isContained))
                    }
                    else {
                        cloned[f.name] = clone(value, f.isContained)
                    }
                }
                else if (f.isArray) {
                    cloned[f.name] = []
                }
            }
            else {
                if (f.isArray) {
                    cloned[f.name] = [...value]
                }
                else {
                    cloned[f.name] = value
                }
            }
        }
        else if (f.isArray) {
            cloned[f.name] = []
        }
    })
    if (!deep && entity.e_id) {
        cloned.e_id = entity.e_id
    }

    return cloned
}

function setDeleted(obj, old2new) {
    return walkObject(obj, (field, index, value, state) => {
        const {old2new} = state
        if (value && field.isContained && (!field.isArray || index >= 0)) {
            old2new.set(value, null)
        }
    }, {old2new}).old2new
}

function diffObjects(nobj, oobj, npath, opath, old2new) {
    getTypeFields(nobj._type_).forEach(field=> {
        if (field.isReference && field.isContained) {
            const newValue = nobj[field.name]
            const oldValue = oobj[field.name]
            if (field.isArray) {
                diffArrays(newValue || [], oldValue || [], npath, opath, old2new)
            }
            else {
                if (newValue !== oldValue && oldValue) {
                    old2new.set(oldValue, newValue)
                    if (newValue) {
                        diffObjects(newValue, oldValue, npath, opath, old2new)
                    }
                    else {
                        setDeleted(oldValue, old2new)
                    }
                }
            }
        }
    })
}

function diffArrays(narr, oarr, npath, opath, old2new) {
    let i = 0
    let j = 0
    while (i < narr.length && j < oarr.length) {
        const n = narr[i]
        const o = oarr[j]
        if (n === o) {
            ++i; ++j;
        }
        else if (opath.has(n)) {
            if (npath.has(o)) {
                ++i; ++j; // order changed (swapped)
            }
            else {
                // deleted object
                old2new.set(o, null)
                setDeleted(o, old2new)
                ++j
            }
        }
        else {
            if (npath.has(o)) {
                // new object
                ++i;
            }
            else {
                // updated object
                ++i; ++j;
                old2new.set(o, n)
                diffObjects(n, o, npath, opath, old2new)            }
        }
    }
    while (i < narr.length) {
        // inserted
        ++i
    }
    while (j < oarr.length) {
        // deleted object
        old2new.set(oarr[j], null)
        setDeleted(oarr[j], old2new)
        ++j
    }
}

function removeNonPersistedReferences(obj, prefix, paths, refs) {
    const nonPersisted = []
    refs.forEach(ref=>{
        const {path} = ref
        const refObj = _.get(obj, path)
        if (refObj && !refObj.e_id) {
            _.set(obj, path, undefined)
            nonPersisted.push(ref)
        }
    })
    return nonPersisted
}

function restoreReferences(obj, refs) {
    refs.forEach(ref=>{
        const {path, refto} = ref
        const value = _.get(obj, refto)
        _.set(obj, path, value)
    })
}

function simplifyNonContainedReferences(obj, refs) {
    refs.forEach(ref=>{
        const {path} = ref
        const value = _.get(obj, path)
        if (value) {
            _.set(obj, path, {_type_: value._type_, e_id: value.e_id, name: value.name})
        }
        else {
            _.set(obj, path, undefined)
        }
    })
}

function modifyBeforeSave(entity) {
    const paths = new Map()
    getPaths(entity, paths, "")
    const ncrefs = findNonContainedReferences(entity, "", paths, [])
    const nprefs = removeNonPersistedReferences(entity, "", paths, ncrefs)
    simplifyNonContainedReferences(entity, ncrefs)
    return (promise)=>{
        if (nprefs.length > 0) {
            promise = promise.then(entity=>{
                restoreReferences(entity, nprefs)
                simplifyNonContainedReferences(entity, ncrefs)
                return resource.saveEntity(entity)
            })
        }
        return promise.then(entity=>{
            return restoreReferencesById(entity, ncrefs)
        })
    }
}

function restoreReferencesById(entity, ncrefs) {
    const id2obj = new Map()
    mapIdToObject(entity, id2obj)
    ncrefs.forEach(ref=>{
        const {path} = ref
        const value = _.get(entity, path)
        if (value && value.e_id) {
            const refObject = id2obj.get(value.e_id)
            if (refObject) {
                _.set(entity, path, refObject)
            }
        }
    })
    return entity
}

function modifyAfterSelect(entity) {
    const paths = new Map()
    getPaths(entity, paths, "")
    const ncrefs = findNonContainedReferences(entity, "", paths, [])
    return restoreReferencesById(entity, ncrefs)
}

function restoreReferencesAfterChange(newEntity, oldEntity) {
    const oldPaths = new Map()
    getPaths(oldEntity, oldPaths, "")
    const newPaths = new Map()
    getPaths(newEntity, newPaths, "")
    const old2new = new Map()
    diffObjects(newEntity, oldEntity, newPaths, oldPaths, old2new)
    const ncrefs = findNonContainedReferences(newEntity, "", newPaths, [])
    ncrefs.forEach(ref=>{
        const {path, refto} = ref
        const value = _.get(newEntity, path)
        if (value && !refto) {
            const oldPath = oldPaths.get(value)
            if (oldPath) {
                const refObject = old2new.get(value)
                //newEntity = _.setWith(_.clone(newEntity), path, refObject, _.clone)
                _.set(newEntity, path, refObject)
            }
        }
    })
    return newEntity
}

function getDisplayFieldName(entity){
    const shortName = getClassDef(entity._type_).displayFieldName
    return shortName ? entity.shortName : entity.name
}

export {getBasename, modifyBeforeSave, modifyAfterSelect, restoreReferencesAfterChange, instantiate, 
    getPaths, getIcon, clone, getDisplayFieldName, findNonContainedReferences, simplifyNonContainedReferences,
    restoreReferences, isEntityEquel}
