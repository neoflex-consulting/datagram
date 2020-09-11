class LinkDescription {
    constructor(start, finish, startPortId, finishPortId) {
        this._start = start
        this._finish = finish
        this._startPortId = startPortId
        this._finishPortId = finishPortId
    }

    get start() {
        return this._start
    }

    set start(value) {
        this._start = value
    }

    get finish() {
        return this._finish
    }

    set finish(value) {
        this._finish = value
    }

    get startPortId() {
        return this._startPortId
    }

    get finishPortId() {
        return this._finishPortId
    }

}

export default LinkDescription;
