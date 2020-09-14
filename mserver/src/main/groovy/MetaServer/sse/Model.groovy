package MetaServer.sse

import MetaServer.rt.LivyServer
import MetaServer.utils.EMF
import MetaServer.utils.HDFSClient
import groovy.json.JsonOutput
import groovy.text.SimpleTemplateEngine
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import ru.neoflex.meta.model.Database

import java.sql.Types

class Model {
    protected final static Log log = LogFactory.getLog(Model.class)

    static Object upload(Map entity, Map params = null) {
        def db = Database.new
        def model = db.get(entity)
        def file = params.file
        model.base64body = file.bytes.encodeBase64().toString()
        model.fileName = file.filename
        def path = "${model.livyServer.home}/${model.livyServer.user}/deployments/models/${model.name}"
        def code = EMF.generate([model],
                "/pim/dataspace/psm/R/runModel.egl", [path: path, fileName: model.fileName])
        model.code = code.replace("\r", "")
        log.info(code)
        db.save(model)
        return [status: file.bytes.size()]
    }

    static Object run(Map entity, Map params = null) {
        def db = Database.new
        def model = db.get(entity)
        def livyServer = model.livyServer
        def body = (String) params.body
        def encBody = body.getBytes("utf-8").encodeBase64()
        def template = new SimpleTemplateEngine().createTemplate(model.code).make([input: encBody])
        def code = template.toString()
        def sessionId = model.sessionId
        def applicationId = null
        for (int count = 0; count < 300 && applicationId == null; ++count) {
            if (sessionId == null) {
                sessionId = LivyServer.getSessionId([:], livyServer)
                model.applicationId = null
            }
            try {
                def info = LivyServer.sessionInfo(livyServer, [sessionId: sessionId])
                if (info.result.appId != null) {
                    if (model.applicationId != null && info.result.appId != model.applicationId) {
                        log.error("Invalid session: " + sessionId)
                        sessionId = null
                        model.applicationId = null
                    }
                    else {
                        applicationId = info.result.appId
                    }
                }
                else {
                    sleep(1000)
                }
            } catch (Throwable e) {
                log.error("Cannot find session id " + sessionId, e)
                sessionId = null
            }
        }
        if (applicationId == null) {
            throw new RuntimeException("Can't create session")
        }
        def result = LivyServer.executeStatementAndWait(sessionId, code, log, livyServer, 'sparkr')
        def jsonData = JsonOutput.toJson([["result": null, "warning": null, "error": "No result"]])
        def resultData = result.output.data
        if (resultData != null) {
            def parsedJson = (resultData instanceof Map) ? ((resultData.values()[0] =~ /(?ms).*^(\{.*\})\s*$.*/)) : (resultData =~ /\{.*\}/)
            if (parsedJson.matches()) {
                jsonData = parsedJson.group(1)
            }
            else {
                jsonData = JsonOutput.toJson([["result": null, "warning": null, "error": result.toString()]])
            }
        }
        else {
            jsonData = JsonOutput.toJson([["result": null, "warning": null, "error": result.output.toString()]])
        }
        model.sessionId = sessionId
        model.applicationId = applicationId
        db.save(model)
        return jsonData
    }

    static Object deploy(Map entity, Map params = null) {
        def db = Database.new
        def model = db.get(entity)
        if (model.base64body == null) {
            throw new RuntimeException("Model body undefined")
        }
        def livyServer = model.livyServer
        def hdfs = new HDFSClient(livyServer.webhdfs, livyServer.user, livyServer)

        def path = "${livyServer.home}/${livyServer.user}/deployments/models/${model.name}"

        hdfs.deleteDir(path)
        hdfs.createDir(path)

        def bytes = model.base64body.decodeBase64()
        hdfs.putBytes("${path}/${model.fileName}", bytes)

        def code = EMF.generate([model],
                "/pim/dataspace/psm/R/runModel.egl", [path: path, fileName: model.fileName])
        model.code = code.replace("\r", "")
        log.info(code)
        db.save(model)
        return [status: bytes.size()]
    }
}
