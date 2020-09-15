package MetaServer.rt;
import ru.neoflex.meta.utils.Context;
/* protected region MetaServer.rtJobServer.inport on begin */
import groovyx.net.http.RESTClient
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import ru.neoflex.meta.utils.JSONHelper;
import ru.neoflex.meta.model.Database;
/* protected region MetaServer.rtJobServer.inport end */
class JobServer {
    /* protected region MetaServer.rtJobServer.statics on begin */
    private final static Log logger = LogFactory.getLog(JobServer.class);

    public static Object getHTTPClient(Map entity) {
        def client = new RESTClient( entity.http )
        client.handler.failure = { resp, reader ->
            [resp:resp, reader:reader]
        }
        client.handler.success = { resp, reader ->
            [resp:resp, reader:reader]
        }
        return client
    }

    public static Map submitJob(Map entity, String appName, String classPath, List jobParams) {
        return submitJobImpl(entity, appName, classPath, jobParams, false)
    }

    public static Map submitJobImpl(Map entity, String appName, String classPath, List jobParams, boolean sync) {
        def query = [appName: appName , sync: sync, classPath: classPath]
        logger.info("Post to\n${entity.http}/jobs\nquery=${query}\nwith params=${jobParams.findAll {!it.toLowerCase().contains("password")}}")
        def data = getHTTPClient(entity).post(
                path : "/jobs",
                requestContentType : groovyx.net.http.ContentType.JSON,
                contentType : groovyx.net.http.ContentType.ANY,
                query : query,
                body : [params: jobParams])
        def status = data.reader?.status
        def result = data.reader?.result
        def message = JSONHelper.pp(result)
        def resp = data.resp
        if (status == "ERROR" || resp.status < 200 || resp.status > 300) {
            logger.error(message)
            return [result: false, problems: [result], data: [:]]
        }
        return [result: true, problems: [], data: [jobId: result.jobId]]
    }
    public static Map postJar(Map entity, String jarName, File file) {
        def respText = null
        def resp = getHTTPClient(entity).post(
                path : "/jars/${jarName}".toString(),
                requestContentType : groovyx.net.http.ContentType.BINARY,
                contentType : groovyx.net.http.ContentType.ANY,
                body : file.bytes) {
            respText = it.entity.content.text
            it
        }
        if (resp.status < 200 || resp.status > 300) {
            logger.error("Post ${file.absolutePath}: ${resp}")
            throw new RuntimeException(resp);
        }
        logger.info("Post ${file.absolutePath}: ${respText}")
        return [result: true, problems:[]]
    }
    public static Map waitingForJob(Map entity, String jobId, int numOfRetries, long sleepInterval) {
        def client = getHTTPClient(entity)
        while (numOfRetries > 0) {
            try {
                Thread.sleep(sleepInterval);
            } catch (InterruptedException e) {
            }
            def map = client.get(
                    path : "/jobs/${jobId}",
                    requestContentType : groovyx.net.http.ContentType.JSON,
                    contentType : groovyx.net.http.ContentType.ANY)
            def status = map?.reader?.get("status")
            def message = JSONHelper.pp(map?.reader?.result)
            if (status in ["FINISHED", "ERROR", "VALIDATION FAILED"]) {
                logger.info("Transformation ${jobId} finished. Status ${status}");
                def problems = []
                def success = !(status in ["ERROR", "VALIDATION FAILED"])
                if (!success) {
                    problems.add([result: false, status: status, message: message])
                    logger.error(message)
                }
                return [result: success, problems:problems, data: [jobDetails: map?.reader]]
            }
            if (numOfRetries%10 == 0) {
                logger.info("[${numOfRetries}] ${status}/${jobId}");
            }
            numOfRetries--;
        }
        return [result: false, problems:["TIMEDOUT"]]
    }
    public static List getJobList(Map entity) {
        def jobList = getHTTPClient(entity).get(
                path : "/jobs",
                requestContentType : groovyx.net.http.ContentType.JSON,
                contentType : groovyx.net.http.ContentType.ANY)?.reader
        return [result: true, problems:[], data: [jobList: jobList]]
    }
    public static Map getJobDetails(Map entity, String jobId) {
        def jobDetails = getHTTPClient(entity).get(
                path : "/jobs/${jobId}",
                requestContentType : groovyx.net.http.ContentType.JSON,
                contentType : groovyx.net.http.ContentType.ANY)?.reader
        return [result: true, problems:[], data: [jobDetails: jobDetails]]
    }
    public static Map getJobConfig(Map entity, String jobId) {
        def jobConfig = getHTTPClient(entity).get(
                path : "/jobs/${jobId}/config",
                requestContentType : groovyx.net.http.ContentType.JSON,
                contentType : groovyx.net.http.ContentType.ANY)?.reader
        return [result: true, problems:[], data: [jobConfig: jobConfig]]
    }
    public static Map runUtility(Map entity, String endpoint, Map params) {
        def jobParams = []
        params.keySet().each {
            jobParams.add(JSONHelper.escape("${it}=${params[it]}"))
        }
        File jarFile = Context.current.contextSvc.classLoaderSvc.findFile("ru.neoflex.meta.etl.spark.jobserver.Utils")
        if (jarFile == null) {
            throw new RuntimeException("jar not found: ru.neoflex.meta.etl.spark.jobserver.Utils")
        }
        postJar(entity, "Utils", jarFile)
        def resp1 = submitJob(entity, "Utils", "ru.neoflex.meta.etl.spark.jobserver.${endpoint}".toString(), jobParams)
        /*if (resp1.result == false && resp1.problems.size() == 1 && resp1.problems[0] == "appName Utils not found") {
            File jarFile = Context.current.contextSvc.classLoaderSvc.findFile("ru.neoflex.meta.etl.spark.jobserver.Utils")
            if (jarFile == null) {
                throw new RuntimeException("jar not found: ru.neoflex.meta.etl.spark.jobserver.Utils")
            }
            postJar(entity, "Utils", jarFile)
            resp1 = submitJob(entity, "Utils", "ru.neoflex.meta.etl.spark.jobserver.${endpoint}".toString(), jobParams)
        }*/
        def jobId = resp1?.data?.jobId
        def resp2 = waitingForJob(entity, jobId, 1000, 1000)
        if (resp2.result == true) {
            resp2.data = resp2.data.jobDetails.result
        }
        return resp2
    }
    public static Map runLoader(Map entity, Map params) {
        def resp = runUtility(entity, "RunLoader", params)
        if (resp.result == true) {
            resp.data.columns = sparkColumnsToModel(resp.data.columns)
        }
        return resp
    }
    public static Map runSQL(Map entity, Map params) {
        def resp = runUtility(entity, "HiveQuery", params)
        if (resp.result == true) {
            resp.data.columns = sparkColumnsToModel(resp.data.columns)
        }
        return resp
    }

    public static List sparkColumnsToModel(List colunms) {
        def db = Database.new
        def result = []
        colunms.each {
            def column = [_type_: "rel.Column", name: it.name, nullable: it.nullable]
            if (it.type == "integer") {
                column.dataType = db.instantiate("rel.INTEGER")
            }
            else if (it.type == "long") {
                column.dataType = db.instantiate("rel.LONG")
            }
            else if (it.type.startsWith("decimal")) {
                column.dataType = db.instantiate("rel.DECIMAL")
            }
            else if (it.type == "boolean") {
                column.dataType = db.instantiate("rel.BOOLEAN")
            }
            else if (it.type == "date") {
                column.dataType = db.instantiate("rel.DATE")
            }
            else if (it.type == "timestamp") {
                column.dataType = db.instantiate("rel.DATETIME")
            }
            else if (it.type == "double") {
                column.dataType = db.instantiate("rel.DOUBLE")
            }
            else if (it.type == "string") {
                column.dataType = db.instantiate("rel.VARCHAR")
            }
            else /*if (it.type == "null")*/ {
                column.dataType = db.instantiate("rel.VARCHAR")
            }
            result.add(column)
        }
        return result
    }
    /* protected region MetaServer.rtJobServer.statics end */

    public static Object jobList(Map entity, Map params = null) {
    /* protected region MetaServer.rtJobServer.jobList on begin */
        def jobList = getJobList(entity)
        jobList.each {logger.info(jobList.toString())}
        return jobList
    /* protected region MetaServer.rtJobServer.jobList end */
    }

    public static Object jobDetails(Map entity, Map params = null) {
    /* protected region MetaServer.rtJobServer.jobDetails on begin */
        def jobDetails = getJobDetails(entity, params.jobId)
        logger.info(jobDetails.toString())
        return jobDetails
    /* protected region MetaServer.rtJobServer.jobDetails end */
    }

    public static Object jobConfig(Map entity, Map params = null) {
    /* protected region MetaServer.rtJobServer.jobConfig on begin */
        def jobConfig = getJobConfig(entity, params.jobId)
        logger.info(jobConfig.toString())
        return jobConfig
    /* protected region MetaServer.rtJobServer.jobConfig end */
    }

    public static Object jobWait(Map entity, Map params = null) {
    /* protected region MetaServer.rtJobServer.jobWait on begin */
        return waitingForJob(entity, params.jobId, new Integer(params.numOfRetries ?: 1000), new Integer(params.sleepInterval ?: 1000))
    /* protected region MetaServer.rtJobServer.jobWait end */
    }

    public static Object loadFile(Map entity, Map params = null) {
    /* protected region MetaServer.rtJobServer.loadFile on begin */
        return runLoader(Database.new.get(entity), params)
    /* protected region MetaServer.rtJobServer.loadFile end */
    }

    public static Object runQuery(Map entity, Map params = null) {
    /* protected region MetaServer.rtJobServer.runQuery on begin */
        return runSQL(Database.new.get(entity), params)
    /* protected region MetaServer.rtJobServer.runQuery end */
    }
}
