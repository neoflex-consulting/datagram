package MetaServer.rt

import MetaServer.utils.REST
import com.google.common.base.Strings
import groovyx.net.http.ContentType
import org.apache.commons.io.IOUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

/* protected region MetaServer.rtLivyServer.inport on begin */

import org.springframework.http.ResponseEntity
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.util.UriUtils
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.Context
import ru.neoflex.meta.utils.ZipUtils

import java.util.zip.ZipOutputStream

/* protected region MetaServer.rtLivyServer.inport end */

class LivyServer {
    private final static Log logger = LogFactory.getLog(LivyServer.class)
    /* protected region MetaServer.rtLivyServer.statics on begin */

    static Object checkSession(Map entity, Map params = null) {
        def data = REST.getHTTPClient(entity).get(
                path: "/sessions/${params.sessionId}/state",
                requestContentType: MediaType.APPLICATION_JSON_UTF8_VALUE,
                contentType: MediaType.APPLICATION_JSON_UTF8_VALUE)?.reader
        try {
            data.state
        } catch (e) {
            throw new RuntimeException(data)
        }
        return [result: data]
    }

    static Object getSessions(Map entity, Map params = null) {
        def sessions = REST.getHTTPClient(entity).get(
                path: "/sessions",
                requestContentType: MediaType.APPLICATION_JSON_UTF8_VALUE,
                contentType: MediaType.APPLICATION_JSON_UTF8_VALUE)?.reader
        return [result: sessions]
    }

    static Object createSession(Map entity, Map params = null) {
        def props = [:]
        if (entity.driverMemory) props["driverMemory"] = entity.driverMemory
        if (entity.executorMemory) props["executorMemory"] = entity.executorMemory
        if (entity.executorCores) props["executorCores"] = entity.executorCores
        if (entity.numExecutors) props["numExecutors"] = entity.numExecutors
        def data = REST.getHTTPClient(entity).post(
                path: "/sessions",
                requestContentType: MediaType.APPLICATION_JSON_UTF8_VALUE,
                contentType: MediaType.APPLICATION_JSON_UTF8_VALUE,
                headers: ["X-Requested-By": InetAddress.getLocalHost().getHostAddress()],
                body: props)?.reader
        return [result: data]
    }

    static Object getIdleSession(Map entity, Map params = null) {
        def sessions = getSessions(entity, params).result.sessions
        def idle = sessions.find { it.state == 'idle' }
        if (idle == null) {
            return createSession(entity, params)
        } else {
            return [result: idle]
        }
    }

    static Object deleteSession(Map entity, Map params = null) {
        def data = REST.getHTTPClient(entity).delete(
                path: "/sessions/${params.sessionId}",
                requestContentType: MediaType.APPLICATION_JSON_UTF8_VALUE,
                headers: ["X-Requested-By": InetAddress.getLocalHost().getHostAddress()],
                contentType: MediaType.APPLICATION_JSON_UTF8_VALUE)?.reader
        return [result: data]
    }

    static Object sessionInfo(Map entity, Map params = null) {
        def session = REST.getHTTPClient(entity).get(
                path: "/sessions/${params.sessionId}",
                requestContentType: MediaType.APPLICATION_JSON_UTF8_VALUE,
                contentType: MediaType.APPLICATION_JSON_UTF8_VALUE)?.reader
        return [result: session]
    }

    static Object executeStatement(Map entity, Map params = null) {
        def livyKindSupportVersion = '0.4'
        def livyVersion = getLivyVersion(entity).substring(0, 3)
        def kind = params.get('kind', 'scala')

        // Поддержка типа интерпретатора появилась начиная с версии 0.4, иначе кидается 400-я ошибка
        def body = livyVersion < livyKindSupportVersion ? [code: params.code] :
                [code: params.code, kind: kind]

        if (livyVersion < livyKindSupportVersion && kind != 'scala') {
            logger.warn("Executing non-spark code as spark on old Livy Server ${livyVersion}")
        }

        def data = REST.getHTTPClient(entity).post(
                path: "/sessions/${params.sessionId}/statements",
                requestContentType: MediaType.APPLICATION_JSON_UTF8_VALUE,
                headers: ["X-Requested-By": InetAddress.getLocalHost().getHostAddress()],
                contentType: MediaType.APPLICATION_JSON_UTF8_VALUE,
                body: body)?.reader
        return [result: data]
    }

    static Object checkStatement(Map entity, Map params = null) {
        def data = REST.getHTTPClient(entity).get(
                path: "/sessions/${params.sessionId}/statements/${params.statementId}",
                requestContentType: MediaType.APPLICATION_JSON_UTF8_VALUE,
                contentType: MediaType.APPLICATION_JSON_UTF8_VALUE)?.reader
        return [result: data]
    }

    static Object getStatements(Map entity, Map params = null) {
        def statements = REST.getHTTPClient(entity).get(
                path: "/sessions/${params.sessionId}/statements",
                requestContentType: MediaType.APPLICATION_JSON_UTF8_VALUE,
                contentType: MediaType.APPLICATION_JSON_UTF8_VALUE)?.reader
        return [result: statements]
    }

    static Object getBatches(Map entity, Map params = null) {
        def batches = REST.getHTTPClient(entity).get(
                path: "/batches",
                requestContentType: MediaType.APPLICATION_JSON_UTF8_VALUE,
                contentType: MediaType.APPLICATION_JSON_UTF8_VALUE)?.reader
        return [result: batches]
    }

    static Object getBatchLog(Map entity, Map params = null) {
        def batchId = params.batchId
        def result = REST.getHTTPClient(entity).get(
                path: "/batches/${batchId}/log",
                requestContentType: MediaType.APPLICATION_JSON_UTF8_VALUE,
                contentType: MediaType.APPLICATION_JSON_UTF8_VALUE)?.reader
        return [result: result.log]
    }

    static Object deleteBatch(Map entity, Map params = null) {
        def data = REST.getHTTPClient(entity).delete(
                path: "/batches/${params.batchId}",
                requestContentType: MediaType.APPLICATION_JSON_UTF8_VALUE,
                headers: ["X-Requested-By": InetAddress.getLocalHost().getHostAddress()],
                contentType: MediaType.APPLICATION_JSON_UTF8_VALUE)?.reader
        return [result: data]
    }

    static Object getBatchSessions(Map entity, Map params = null) {
        def sessions = REST.getHTTPClient(entity).get(
                path: "/batches",
                requestContentType: MediaType.APPLICATION_JSON_UTF8_VALUE,
                contentType: MediaType.APPLICATION_JSON_UTF8_VALUE)?.reader
        return [result: sessions]
    }

    static Object checkBatchSession(Map entity, Map params = null) {
        def data = REST.getHTTPClient(entity).get(
                path: "/batches/${params.sessionId}/state",
                requestContentType: MediaType.APPLICATION_JSON_UTF8_VALUE,
                contentType: MediaType.APPLICATION_JSON_UTF8_VALUE)?.reader
        try {
            data.state
        } catch (e) {
            throw new RuntimeException(data)
        }
        return [result: data.state]
    }

    static Object createBatchSession(Map entity, Map params = null) {
        def data = REST.getHTTPClient(entity).post(
                path: "/batches",
                requestContentType: MediaType.APPLICATION_JSON_UTF8_VALUE,
                contentType: MediaType.APPLICATION_JSON_UTF8_VALUE,
                headers: ["X-Requested-By": InetAddress.getLocalHost().getHostAddress()],
                body: params)?.reader
        return [result: data]
    }

    static Object deleteBatchSession(Map entity, Map params = null) {
        def data = REST.getHTTPClient(entity).delete(
                path: "/batches/${params.sessionId}",
                requestContentType: MediaType.APPLICATION_JSON_UTF8_VALUE,
                headers: ["X-Requested-By": InetAddress.getLocalHost().getHostAddress()],
                contentType: MediaType.APPLICATION_JSON_UTF8_VALUE)?.reader
        return [result: data]
    }

    static Object batchSessionInfo(Map entity, Map params = null) {
        def session = REST.getHTTPClient(entity).get(
                path: "/batches/${params.sessionId}",
                requestContentType: MediaType.APPLICATION_JSON_UTF8_VALUE,
                contentType: MediaType.APPLICATION_JSON_UTF8_VALUE)?.reader
        return [result: session]
    }

    static Object listFiles(Map entity, Map params = null) {

        def path = params.path

        return getFileStatus(entity, path as String)
    }


    private static String getLivyUser(Map livyServer) {
        if (livyServer.user == null || livyServer.user == "") {
            return "hdfs";
        }
        return livyServer.user;
    }

    private static String getWebHDFS(Map livyServer) {

        String addressProperty = livyServer.webhdfs;
        if (addressProperty.indexOf(";") < 0) {
            return addressProperty;
        }

        List<String> addressArray = Arrays.asList(addressProperty.split(";"));
        for (String address : addressArray) {
            try {
                URLConnection connection = new URL(address).openConnection();
                connection.setConnectTimeout(3000);
                connection.connect();
                return address;
            } catch (Exception e) {
            }
        }
        return "";
    }


    private static Map getFileStatus(Map livyServer, String path) {

        def http = REST.getHTTPClient(getWebHDFS(livyServer) + "/", livyServer)
        def user = getLivyUser(livyServer)
        def fileStatus = http.get([
                path              : UriUtils.encodePath(path, "UTF-8").substring(1),
                requestContentType: ContentType.ANY,
                contentType       : MediaType.APPLICATION_JSON_UTF8_VALUE,
                query             : ['user.name': user, 'op': "GETFILESTATUS"]
        ]).reader.FileStatus
        fileStatus.children = []
        if (fileStatus.type == "DIRECTORY") {
            fileStatus.children = http.get([
                    path              : path.substring(1),
                    requestContentType: ContentType.ANY,
                    contentType       : MediaType.APPLICATION_JSON_UTF8_VALUE,
                    query             : ['user.name': user, 'op': "LISTSTATUS"]
            ]).reader.FileStatuses.FileStatus.collect({
                it.name = it.pathSuffix
                it
            })
        }
        return fileStatus
    }

    private static void zipDirectory(Map livyServer, String path, List children, ZipOutputStream zipOutputStream) {
        ZipUtils.zipDirectoryEntry(path, zipOutputStream)
        if (path[path.length() - 1] != '/') {
            path = path + "/"
        }
        children.each {
            def newPath = path + it.name as String
            if (it.type == "DIRECTORY") {
                def status = getFileStatus(livyServer, newPath)
                zipDirectory(livyServer, newPath, status.children as List, zipOutputStream)
            } else if (it.type == "FILE") {
                InputStream is = getFileInputStream(livyServer, newPath)
                try {
                    ZipUtils.zipInputStream(newPath, is, zipOutputStream)
                }
                finally {
                    if (is != null) {
                        is.close()
                    }
                }
            }

        }
    }

    static Object getFile(Map entity, Map params = null) {
        def path = params.path as String
        def status = getFileStatus(entity, path)
        if (status.type == "FILE") {
            InputStream inputStream = getFileInputStream(entity, path)
            return new ResponseEntity(new InputStreamResource(inputStream), HttpStatus.OK)
        }
        PipedInputStream pipedInputStream = new PipedInputStream()
        PipedOutputStream pipedOutputStream = new PipedOutputStream(pipedInputStream)
        new Thread() {
            void run() {
                try {
                    ZipOutputStream zipOutputStream = new java.util.zip.ZipOutputStream(pipedOutputStream)
                    try {
                        zipDirectory(entity, path, status.children, zipOutputStream)
                    }
                    finally {
                        zipOutputStream.close()
                    }
                } catch (IOException e) {
                    logger.error("Failed to zip directory", e)
                    throw new RuntimeException(e)
                }
            }
        }.start()
        HttpHeaders headers = new HttpHeaders()
        headers.set("Content-Type", "application/zip")
        headers.set("Content-Disposition", "attachment; filename=\"${new File(path).name}.zip\"")
        return new ResponseEntity(new InputStreamResource(pipedInputStream), headers, HttpStatus.OK)
    }

    private static InputStream getFileInputStream(Map livyServer, String path) {
        Map entity
        def http = REST.getSimpleHTTPClient(getWebHDFS(livyServer) + "/", livyServer)
        def user = getLivyUser(livyServer)
        def response = http.get([
                path              : UriUtils.encodePath(path, "UTF-8").substring(1),
                requestContentType: ContentType.ANY,
                contentType       : ContentType.ANY,
                query             : ['user.name': user, 'op': "OPEN"]
        ])
        def inputStream = response.data as InputStream
        inputStream
    }

    static Object uploadFile(Map entity, Map params = null) {
        def path = params.path as String
        def file = params.file as MultipartFile
        uploadInputStream(entity, "${path}/${file.filename}", file.inputStream)
        return [status: "OK"]
    }

    private static void uploadInputStream(Map livyServer, String newFileName, InputStream inputStream) {
        def http = REST.getHTTPClient(getWebHDFS(livyServer) + "/", livyServer)
        def user = getLivyUser(livyServer)
        def put1 = http.put(
                path: UriUtils.encodePath(newFileName, "UTF-8").substring(1),
                query: ['user.name': user, 'op': "CREATE", 'overwrite': 'true']
        )
        def put2 = http.put(
                uri: put1.resp.headers.location,
                requestContentType: ContentType.BINARY,
                contentType: ContentType.ANY,
                body: inputStream
        )
    }

    static Object deleteFile(Map entity, Map params = null) {
        def path = params.path as String
        def http = REST.getHTTPClient(entity.webhdfs + "/", entity)
        def resp = http.delete(
                path: UriUtils.encodePath(path, "UTF-8").substring(1),
                query: ['user.name': entity.user, 'op': "DELETE", 'recursive': 'true']
        )
        return resp.reader
    }

    static Object findCurrentLivyServer(Map trDeployment, Map params = null) {
        def livyServer

        if (params != null && params.livyServer != null) livyServer = params.livyServer
        else if (trDeployment != null && trDeployment.livyServer != null) livyServer = trDeployment.livyServer
        if (livyServer == null) livyServer = Database.new.list('rt.LivyServer', ['isDefault': true]).first()

        if (livyServer == null) throw new RuntimeException("Livy Server not found")
        return livyServer
    }

    static Integer getSessionId(Map params = null, Map livyServer) {
        def sessionId = (params ? params : [:]).sessionId
        if (sessionId != null) {
            try {
                def state = checkSession(livyServer, [sessionId: sessionId]).result.state
                if (state == null || state == "dead") {
                    sessionId = null
                }
            }
            catch (Throwable th) {
                sessionId = null
            }
        }
        if (sessionId == null) {
            def result = getIdleSession(livyServer).result
            try {
                sessionId = result.id
            } catch (e) {
                throw new RuntimeException(result)
            }
        }
        if (sessionId instanceof String) sessionId = Integer.parseInt(sessionId)
        return sessionId
    }

    static Map parseResult(result, outputType, sessionId) {
        if (result.output.status == 'error') {
            logger.error(result.output.evalue)
            return [result: "text/plain:${result.output}", sessionId: sessionId]
        } else {
            result = result.output.data
            if (outputType == 'json') {
                def jsonData = "{fields:[]}"
                if (result != null) {
                    def parsedJson = (result instanceof Map) ? ((result.values()[0] =~ /(?ms).*^(\{.*\})$.*/)) : (result =~ /\{.*\}/)
                    if (parsedJson.matches()) {
                        jsonData = parsedJson.group(1)
                    }
                }
                return [result: jsonData, sessionId: sessionId]
            } else {
                return [result: result, sessionId: sessionId]
            }
        }
    }

    static Object executeStatementAndWait(Integer sessionId, String code, Log logger, Map livyServer, String kind = 'spark') {
        def result
        def statementId
        logger.info("sessionId:" + sessionId)
        def timeout = 500

        while (checkSession(livyServer, [sessionId: sessionId]).result.state == 'starting') {
            sleep(timeout)
        }
        result = executeStatement(livyServer, [sessionId: sessionId, code: code, kind: kind]).result
        try {
            statementId = result.id
        } catch (e) {
            throw new RuntimeException(result)
        }
        logger.info("statementId:" + statementId)

        def livyState = checkStatement(livyServer, [sessionId: sessionId, statementId: statementId]).result.state
        while (livyState == 'running' || livyState == 'waiting') {
            sleep(timeout)
            livyState = checkStatement(livyServer, [sessionId: sessionId, statementId: statementId]).result.state
        }
        result = checkStatement(livyServer, [sessionId: sessionId, statementId: statementId]).result
        return result
    }

    static Object runCode(Map entity, Map params = null) {
        def livyServer = Database.new.get(entity)
        def sessionId = getSessionId(params, livyServer)
        def result = executeStatementAndWait(sessionId, (String) params.code, logger, livyServer, params.get('kind', 'spark'))
        result.sessionId = sessionId
        return result
    }

    static String getLivyVersion(Map livyServer) {
        def data = REST.getHTTPClient(livyServer).get(
                path: "/version",
                requestContentType: MediaType.APPLICATION_JSON_UTF8_VALUE,
                contentType: MediaType.APPLICATION_JSON_UTF8_VALUE)?.reader
        return Strings.isNullOrEmpty((String) data?.version) ? "" : data.version
    }

    /* protected region MetaServer.rtLivyServer.statics end */

    static Map getDefaultLivyServer() {
        def list = Context.current.session.createQuery("from rt.LivyServer where isDefault=true").list()
        if (list.size() == 0) {
            throw new RuntimeException("No default Livy Server")
        }
        if (list.size() > 1) {
            def servers = list.collect { it.name }.join(", ")
            throw new RuntimeException("No unique default Livy Servers: ${servers}")
        }
        return list.get(0)
    }

    static List downloadDirectory(Map livyServer, String path, File dataDir) {
        def result = []
        def status = getFileStatus(livyServer, path)
        if (status.type == "DIRECTORY") {
            if (path[path.length() - 1] != '/') {
                path = path + "/"
            }
            status.children.each {
                def subList = downloadDirectory(livyServer, path + it.name, dataDir)
                result.addAll(subList)
            }
        } else if (status.type == "FILE") {
            logger.info("Donnloading ${path}".toString())
            def inputStream = getFileInputStream(livyServer, path)
            try {
                def outFile = new File(dataDir, path)
                outFile.getParentFile().mkdirs()
                def outputStream = outFile.newOutputStream()
                try {
                    IOUtils.copy(inputStream, outputStream)
                    result.add(path)
                }
                finally {
                    outputStream.close()
                }
            }
            finally {
                inputStream.close()
            }
        }
        return result
    }

    static List uploadDirectory(Map livyServer, File dataDir, String path) {
        def result = []
        if (path[path.length() - 1] != '/') {
            path = path + "/"
        }
        dataDir.listFiles().each {
            def subPath = path + it.name
            def subFile = new File(dataDir, it.name)
            if (it.isDirectory()) {
                result.addAll(uploadDirectory(livyServer, subFile, subPath))
            } else if (it.isFile()) {
                def inputStream = it.newInputStream()
                try {
                    try {
                        uploadInputStream(livyServer, subPath, inputStream)
                        result.add(subPath)
                    }
                    catch (Throwable e) {
                        logger.info("Failed to upload ${it.absolutePath}\n".toString(), e)
                    }
                }
                finally {
                    inputStream.close()
                }
            }
        }
        return result
    }
}
