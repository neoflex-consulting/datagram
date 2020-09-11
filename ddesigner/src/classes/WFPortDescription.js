class WFPortDescription {
    constructor(portDefinition, portObject, node) {
        this._node = node
        this._portObject = portObject
        this._portDefinition = portDefinition
    }

    get port() {
        return this._port
    }

    get portDefinition() {
        return this._portDefinition
    }

    get node() {
        return this._node
    }

    group() {
        return this._portDefinition.group
    }

    getId() {
        if(this._portDefinition.multiple) {
            return this._node[this._portDefinition.attribute].indexOf(this._portObject).toString()
        } else {
            return this._portDefinition.id
        }

    }

    getTargetNode() {
        let target;
        if(!this._portDefinition._type_ || (this._portDefinition._type_ && this._portDefinition._type_ === "etl.WFNode")) {
            target =  this._portObject;
        } else {
            target =  this._portObject[this._portDefinition.attribute2];
        }
        return target && target._type_ ? target : undefined
    }

}

export default WFPortDescription;
