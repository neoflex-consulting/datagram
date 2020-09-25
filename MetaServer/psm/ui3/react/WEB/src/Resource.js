import _ from 'lodash'
import SockJS from 'sockjs-client'
import Stomp from 'stompjs'

class Resource {

    constructor() {
        this.fetchCount = 0;
        this.fetchCountCB = null;
        this.errorCB = null;
        this.infoCB = null;
        this.logCB = null;
        this.loginCB = (show)=>{};
        this.loginSuccessCB = null
        this.pending = [];
        this.user = undefined
        this.stompClient = null
        this.stompConnectedCB = null
        this.stompReconnect = true
        this.stompEnabled = true
        this.stompReconnectCount = 0
        this.loginIfNotLogged()
    }

    setStompConnected(value) {
        if (this.stompConnectedCB) {
            this.stompConnectedCB(value)
        }
    }

    setStompConnectedCB(value) {
        this.stompConnectedCB = value
    }

    stompConnect() {
        this.stompReconnect = true
        var socket = new SockJS('/mserver-websocket');
        this.stompClient = Stomp.over(socket);
        this.stompClient.connect({}, (frame) => {
            this.stompReconnectCount = 0
            this.setStompConnected(true)
            console.log('STOMP Connected: ' + frame);
            this.stompClient.subscribe(`/user/${this.user}/queue/log`, (message) => {
                this.logLog(JSON.parse(message.body).message);
            });
        }, (error) => {
            this.setStompConnected(false)
            if (this.stompReconnect) {
                this.stompReconnectCount += 1
                console.error(error, "Reconnecting... (", this.stompReconnectCount, ")")
                setTimeout(()=>{this.stompConnect()}, 5000)
            }
        });
    }
    stompDisconnect() {
        this.stompReconnect = false
        if (this.stompClient !== null) {
            this.stompClient.disconnect();
        }
        this.setStompConnected(false)
    }
    loginSuccess(name) {
    	this.user = name
    	if (this.loginSuccessCB) {
    		this.loginSuccessCB(name)
    	}
    }
    authenticate(login, password) {
    	const auth = (login && {
            'Authorization': "Basic " + btoa(login + ":" + password)
        }) || {}
        this.setFetchCount(this.fetchCount + 1);
        return fetch('/system/user', this.getOpts({headers: auth})).then(response=>{
            if (!response.ok) {
                throw Error(response.statusText)
            }
            this.setFetchCount(this.fetchCount - 1);
            return response.json()
        }).then(json=>{
            if (json.name) {
                this.loginSuccess(json.name)
                if (this.stompEnabled) {
                    this.stompConnect()
                }
                this.resendPending()
            }
        }).catch(error=>{
            this.setFetchCount(this.fetchCount - 1);
            if (login) {
                this.logError(error.message);
            }
        })
    }
    logout() {
        this.setFetchCount(this.fetchCount + 1);
        this.loginSuccess(undefined)
        fetch('/logout', this.getOpts({
        	method: "POST"
        })).then(response=>{
            if (!response.ok) {
                throw Error(response.statusText)
            }
            this.setFetchCount(this.fetchCount - 1);
        }).catch(error=>{
            this.setFetchCount(this.fetchCount - 1);
            this.logError(error.message);
        })
        this.stompDisconnect()
    }
    resendPending() {
        this.pending.forEach((item)=>{
            let {url, opts, promise} = item
            this.queryResponse(url, opts).then(response=>{
            	promise.doResolve(response)
            })
        })
        this.pending.length = 0
    }
    cancel() {
    	this.loginCB(false)
        this.pending.forEach((item)=>{
            let {promise} = item
            promise.doReject({message: 'Cancelled by user'})
        })
        this.pending.length = 0
    }
    loginIfNotLogged() {
        if (!this.user) {
            if (this.pending.length > 0) {
                this.loginCB(true)
            }
        }
        else {
            this.resendPending()
        }
        window.setTimeout(()=>{
            this.loginIfNotLogged()
        }, 1000);
    }

    setFetchCount(count) {
        if (count >= 0) {
          this.fetchCount = count;
          if (this.fetchCountCB) {
              this.fetchCountCB(this.fetchCount);
          }
        }
    }

    setFetchCountCB(cb) {
        this.fetchCountCB = cb
        cb(this.fetchCount)
    }

    logError(error, headline) {
        console.log((headline || "") + ": " + error)
    	if (error) {
            if (this.errorCB) {
                this.errorCB(error, headline)
            }
    	}
    }

    logInfo(info, headline) {
        console.log((headline || "") + ": " + info)
        if (this.infoCB) {
            this.infoCB(info, headline)
        }
    }

    logLog(info) {
        if (this.logCB) {
            this.logCB(info)
        }
    }

    setAlertCB(infoCB, errorCB) {
        this.errorCB = errorCB;
        this.infoCB = infoCB;
    }

    setLogCB(logCB) {
        this.logCB = logCB;
    }

    pushPending(url, opts) {
        let cbs = {}
        let promise = new Promise((resolve, reject)=>{
            cbs = {resolve, reject}
        })
        promise.doResolve = cbs.resolve;
        promise.doReject = cbs.reject;
        this.pending.push({url, opts, promise})
        return promise
    }
    getOpts(opts) {
        return _.merge({
            "credentials": "include",
            headers:{'X-Requested-With': 'XMLHttpRequest'}
        }, opts || {})
    }
    queryResponse(url, opts) {
        if (!this.user) {
            return this.pushPending(url, opts)
        }
        this.setFetchCount(this.fetchCount + 1);
        return fetch(url, this.getOpts(opts)).then(response=>{
            if (!response.ok) {
                if (response.status === 401) {
                    this.loginSuccess(undefined)
		            this.setFetchCount(this.fetchCount - 1);
                    return this.pushPending(url, opts)
                }
                response.json().then(json=>{
                    this.logError(json.message, response.statusText);
                }).catch(error=>{this.logError(response.statusText)})
                throw Error()
            }
            this.setFetchCount(this.fetchCount - 1);
            return response
        }).catch(error=>{
            this.setFetchCount(this.fetchCount - 1);
            if (error.message) {
                this.logError(error.message)
            }
            return Promise.reject()
        })
    }

    query(url, opts) {
        return this.queryResponse(url, opts).then(response=>response.json())
    }

    getSimpleList(className, args) {
        const where = "where 1=1" + Object.keys(args || {}).map((key, i)=>` and ${key}=:p${i}`).join()
        const filter = Object.keys(args || {}).map((key, i)=>`&p${i}=${encodeURIComponent(args[key])}`).join()
        return this.query(`/api/teneo/select/from ${className} ${where}?__up=0&__down=0&__deep=0${filter}`)
    }

    getSimpleSelect(className, cols, args) {
        const selectList = ['type(e)', 'e.e_id', ...cols.map(col=>`e.${col}`)].join(',')
        let where = ""
        if (_.size(args) > 0) {
            where = " where " + Object.keys(args || {}).map((key, i)=>`${key}=:p${i}`).join(' and ')
        }
        let filter = ""
        if (_.size(args) > 0) {
            filter = "?" + Object.keys(args || {}).map((key, i)=>`p${i}=${encodeURIComponent(args[key])}`).join('&')
        }
        return this.query(`/api/teneo/select/select ${selectList} from ${className} e${where}${filter}`)
            .then(list=>list.map(row=>_.zipObject(['_type_', 'e_id', ...cols], row)))
    }

    getList(className) {
        return this.query(`/api/fast/${className}`)
    }

    getEntityAttribute(className, name, e_id) {
    	  const [...path] = name.split('.')
        const attr = path.pop()
    	  const joinClause = path.map((part, i)=>`inner join t${i}.${part} as t${i + 1}`).join(' ')
        return this.query(`/api/teneo/select/select t${path.length}.${attr} from ${className} as t0 ${joinClause} where t0.e_id=${e_id}`).then(([attr])=>attr)
    }

    getEntity(className, e_id) {
        return this.query(`/api/deep/${className}/${e_id}`)
    }

    copyEntity(className, e_id, name) {
        return this.query(`/api/teneo/copy/${className}/${e_id}?name=${name}`)
    }

    saveEntity(entity) {
        return this.query(`/api/teneo/${entity._type_}?__up=0&__down=999999&__deep=1`, {
        	method: "POST",
            headers: {
        	    'Content-Type': 'application/json'
            },
            body: JSON.stringify(entity)
        }).then(entity=>this.getEntity(entity._type_, entity.e_id))
    }

    deleteEntity(entity) {
        return this.query(`/api/teneo/${entity._type_}/${entity.e_id}`, {method: "DELETE"})
    }

    callResponse(entity, method, args) {
    	let [ePackage, eClass] = entity._type_.split('.', 2)
        let filter = ""
        if (_.size(args) > 0) {
            filter = "?" + Object.keys(args || {}).map((key)=>`${key}=${encodeURIComponent(args[key])}`).join('&')
        }
        return this.queryResponse(`/api/operation/MetaServer/${ePackage}/${eClass}/${method}${filter}`, {
        	method: "POST",
            headers: {
        	    'Content-Type': 'application/json'
            },           
            body: JSON.stringify(entity)
        })
    }

    call(entity, method, args) {
    	return this.callResponse(entity, method, args).then(response=>response.json())
    }

    callByNameResponse(entity, method, args) {
    	let [ePackage, eClass] = entity._type_.split('.', 2)
        let form = new FormData()
        Object.keys(args || {}).forEach((key)=>{
	        form.append(key, args[key])
        })
        return this.queryResponse(`/api/operation/MetaServer/${ePackage}/${eClass}/${entity.name}/${method}`, {
        	method: "POST",
            body: form
        })
    }

    callByName(entity, method, args) {
    	return this.callByNameResponse(entity, method, args).then(response=>response.json())
    }

    download(entity, method, args) {
    	var filename = "project.zip";
    	return this.callResponse(entity, method, args).then(response=> {
		    var disposition = response.headers.get('Content-Disposition');
		    if (disposition) {
		        var filenameRegex = /filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/;
		        var matches = filenameRegex.exec(disposition);
		        if (matches != null && matches[1]) { 
		          filename = matches[1].replace(/['"]/g, '');
		        }
		    }    		
    		return response.blob()
    	}).then(blob=>{
            if (!this.a) {
                this.a = document.createElement("a");
                document.body.appendChild(this.a);
                this.a.style = "display: none";
            }
            let objectURL = URL.createObjectURL(blob)
            this.a.href = objectURL;
            this.a.download = filename;
            this.a.click();
            URL.revokeObjectURL(objectURL)
        })
    }

}

let resource = new Resource()

export default resource