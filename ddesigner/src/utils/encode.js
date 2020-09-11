import _ from "lodash";

function utoa(str) {
    return window.btoa(unescape(encodeURIComponent(str)));
}
// base64 encoded ascii to ucs-2 string
function atou(str) {
    return decodeURIComponent(escape(window.atob(str)));
}

function encodeObject(obj) {
    return utoa(JSON.stringify(obj, (k, v) => v === undefined ? null : v))
}

function decodeObject(str64) {
    return _.mapValues(JSON.parse(atou(str64)), v => v === null ? undefined : v)
}

function encodePath(path) {
    return path.map(p => encodeObject(p)).join('/')
}

function decodePath(pathStr) {
    return (pathStr || "").replace("/", "").split("/").filter(s => s && s.length > 0).map(s => decodeObject(s))
}

function createHrefWithNewObject(location, object) {
    const urlParams = new URLSearchParams(location.search)
    let path = search2path(urlParams)
    path = addObjectToPath(path, object)
    const href = createHRef(location.pathname, path)
    return href
}

function buildPath(urlParams) {
    return [{name: "Datagram", _type_: "ui3.Application"}, ...search2path(urlParams)]
}

function search2path(urlParams) {
    const pathStr = urlParams.get('path')
    const path = decodePath(pathStr)
    return path
}

function createHRef(pathname, path, args) {
    let query = "path=/" + encodePath(path.filter(p => p._type_ !== 'ui3.Application'))
    if (args) {
        query = query + '&' + Object.keys(args).map(key => `${key}=${args[key]}`).join('&')
    }
    return `${pathname}?${query}`
}

function getObjectId(object) {
    return object._type_ + object.e_id + object.name
}

function addObjectToPath(path, object, options) {
    options = options || {}
    path = [...path]
    if (options.replace && path.length > 0) {
        path.length = path.length - 1
    }
    if (object) {
        var alreadyIndex = path.findIndex((element, index, array) => {
            return getObjectId(element) === getObjectId(object)
        })

        if (alreadyIndex === -1) {
            path.push(object)
        } else {
            path.length = alreadyIndex + 1
        }
    }
    return path
}


export {encodePath, decodePath, addObjectToPath, buildPath, createHRef, createHrefWithNewObject}