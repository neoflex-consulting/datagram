package MetaServer.rt

import MetaServer.etl.Transformation
import MetaServer.utils.EMF
import MetaServer.utils.FileSystem
import MetaServer.utils.GenerationBase
import MetaServer.utils.HDFSClient
import com.google.common.io.Files
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.eclipse.epsilon.common.util.StringProperties
import org.eclipse.epsilon.emc.emf.EmfModel
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.svc.GitflowSvc
import ru.neoflex.meta.utils.Context

/* protected region MetaServer.rtTransformationDeployment.inport on begin */
import ru.neoflex.meta.utils.JSONHelper
import ru.neoflex.meta.utils.SymmetricCipher

import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.function.BiFunction
import java.util.function.Function

import static java.nio.file.Files.readAllBytes
import static java.nio.file.Files.write
import static java.nio.file.Paths.get

/* protected region MetaServer.rtTransformationDeployment.inport end */

class TransformationDeployment extends GenerationBase {
    /* protected region MetaServer.rtTransformationDeployment.statics on begin */
    private final static Log logger = LogFactory.getLog(TransformationDeployment.class)

    static Object chain(fs, Map entity, Map params = null) {
        def problems = []
        def data = params ?: [:]
        for (f in fs) {
            def ret = f(entity, data)
            problems += ret.problems
            data.putAll(ret.data ?: [:])
            if (!ret.result) return [result: false, problems: problems, data: data]
        }
        return [result: true, problems: problems, data: data]
    }

    static Object validateModel(Map entity, Map params = null) {
        def trDeployment = Database.new.get(entity)
        def problems = []
        Transformation.validateModel(trDeployment.transformation)
        return [result: problems.find { it.isCritique == false } == null, problems: problems]
    }

    static Object getLastUpdated(Map e) {
        if (e.auditInfo == null) {
            return null
        }
        return e.auditInfo.changeDateTime == null ? e.auditInfo.createDateTime : e.auditInfo.changeDateTime
    }

    static Object getMaxUpdated(List ents) {
        def result = null
        ents.each {
            def lastUpdated = getLastUpdated(it)
            if (result == null || lastUpdated != null && lastUpdated.after(result)) {
                result = lastUpdated
            }
        }
        return result
    }

    static Object generateAndRunNoWait(Map entity, Map params = null) {
        return chain([
                TransformationDeployment.&install,
                TransformationDeployment.&run
        ], entity, params)
    }

    static Object waitJob(Map entity, Map params = null) {
        def sessionState = 'running'
        def problems = []
        while (sessionState != 'error' && sessionState != 'dead' && sessionState != 'success') {
            sessionState = LivyServer.checkBatchSession(Database.new.get(entity).livyServer, [sessionId: params.sessionId]).result
            sleep(params.timeout ?: 500)
        }
        def message = "Batch ${params.sessionId} finished with status ${sessionState}\nSee Batches in Livy Console for details".toString()
        if (sessionState != 'success') {
            problems += [isCritique: true, constraint: "BatchSuccess", context: "${entity._type_}[${entity.e_id}]".toString(), message: message]
        }
        logger.info(message)
        return [result: sessionState == 'success', problems: problems]
    }

    static Object readFile(Map entity, Map params = null) {
        def generateResult = chain([
                TransformationDeployment.&generate
        ], entity, params)
        if (generateResult.result) {
            def trDeployment = new Database("teneo").get("rt.TransformationDeployment", (Long) entity.e_id)
            def transformation = trDeployment.transformation
            def gitFlow = Context.current.getContextSvc().getGitflowSvc()
            return gitFlow.inCopy(gitFlow.SOURCES + "/Transformation/${transformation.name}/src/main/scala", null, new Function<File, Map>() {
                @Override
                Map apply(File dir) {
                    File file = new File(dir, "${transformation.name}Job.scala")
                    return [result: true, fileContent: new String(readAllBytes(get(file.getPath())))]
                }
            })
        } else {
            return generateResult
        }
    }

    static boolean writeFile(Map entity, Map params = null, String fileContent) {
        def trDeployment = new Database("teneo").get("rt.TransformationDeployment", (Long) entity.e_id)
        def transformation = trDeployment.transformation
        def gitFlow = Context.current.getContextSvc().getGitflowSvc()
        boolean statusFileExists = gitFlow.inGitTransaction(null, new Callable<Boolean>() {
            @Override
            Boolean call() throws Exception {
                Path statusPath = gitFlow.getCurrentGfs().getRootPath().resolve(gitFlow.SOURCES + "/Transformation/${transformation.name}/status.txt")
                return java.nio.file.Files.isRegularFile(statusPath)
            }
        })
        def generateResult = [result: true]
        if (!statusFileExists) {
            generateResult = chain([
                    TransformationDeployment.&generate
            ], entity, params)
        }
        if (generateResult.result) {
            return gitFlow.inCopy(gitFlow.SOURCES + "/Transformation/${transformation.name}/src/main/scala", "${transformation.name}Job.scala", new Function<File, Map>() {
                @Override
                Map apply(File dir) {
                    File file = new File(dir, "${transformation.name}Job.scala")
                    java.nio.file.Files.write(file.toPath(), fileContent.getBytes())
                    return [result: true, fileContent: new String(java.nio.file.Files.readAllBytes(file.toPath()))]
                }
            })
        } else {
            return generateResult
        }
    }

    /* protected region MetaServer.rtTransformationDeployment.statics end */

    static Object validate(Map entity, Map params = null) {
        /* protected region MetaServer.rtTransformationDeployment.validate on begin */
        def trDeployment = Database.new.get(entity)
        def retTD = EMF.validate(trDeployment, "/pim/rt/rt.evl")
        def retT = trDeployment.transformation != null ? Transformation.validate(trDeployment.transformation) : [result: true, problems: []]
        def result = [result: retTD.result && retT.result, problems: retTD.problems + retT.problems]
        if (!result.result) {
            logger.warn(result.problems.collect { it.message }.join("\n"))
        }
        return result
        /* protected region MetaServer.rtTransformationDeployment.validate end */
    }

    static Object generate(Map entity, Map params = null) {
        /* protected region MetaServer.rtTransformationDeployment.generate on begin */
        def retModel = validateModel(entity)
        if (!retModel.result) {
            for (problem in retModel.problems) {
                logger.error(problem)
            }
            return retModel
        }
        def trDeployment = new Database("teneo").get("rt.TransformationDeployment", (Long) entity.e_id)
        def transformation = trDeployment.transformation
        println(transformation.name)
        Transformation.generate(transformation, params)
        //config file
        LinkedHashMap<String, Object> batchParams = getBatchParams(trDeployment, trDeployment.transformation, params)
        def config = EMF.generate([trDeployment], "/psm/etl/airflow/config.egl", [batchParams: batchParams])
        def gitFlow = Context.current.getContextSvc().getGitflowSvc()
        def deploymentDir = gitFlow.SOURCES + "/TransformationDeployment/${trDeployment.name}"
        return gitFlow.inCopy(deploymentDir, "generate config file", new Function<File, Map>() {
            @Override
            Map apply(File dir) {
                def configFile = new File(dir, "${trDeployment.transformation.name}.json")
                if(!configFile.exists()){
                    configFile.createNewFile()
                }
                configFile << config
                return [result: true, problems: []]
            }
        })
        /* protected region MetaServer.rtTransformationDeployment.generate end */
    }

    static Object generatePart(Map entity, Map params = null) {
        /* protected region MetaServer.rtTransformationDeployment.generatePart on begin */
        def retModel = validateModel(entity)
        if (!retModel.result) {
            for (problem in retModel.problems) {
                logger.error(problem)
            }
            return retModel
        }
        def trDeployment = new Database("teneo").get("rt.TransformationDeployment", (Long) entity.e_id)
        def transformation = trDeployment.transformation
        def emfModel = new EmfModel()
        def properties = new StringProperties()
        properties.put(EmfModel.PROPERTY_NAME, "src")
        properties.put(EmfModel.PROPERTY_MODEL_URI, "hibernate://?dsname=teneo&query1=from etl.Transformation where e_id=${transformation.e_id}")
        properties.put(EmfModel.PROPERTY_METAMODEL_URI, "http://www.neoflex.ru/meta/etl")
        properties.put(EmfModel.PROPERTY_READONLOAD, "true")
        emfModel.load(properties, "")
        def workflowId = "${transformation.name}_${System.identityHashCode([])}"
        def jobParams = [
                "HOME=${trDeployment.livyServer.home}".toString(),
                "USER=${trDeployment.livyServer.user}".toString(),
                "WF_HOME=${trDeployment.livyServer.home}/${trDeployment.livyServer.user}".toString(),
                "ROOT_WORKFLOW_ID=${workflowId}".toString(),
                "CURRENT_WORKFLOW_ID=${workflowId}".toString(),
                "SLIDE_SIZE=${trDeployment.slideSize}".toString(),
                "FETCH_SIZE=${trDeployment.fetchSize}".toString(),
                "PARTITION_NUM=${trDeployment.partitionNum}".toString(),
                "FAIL_THRESHOLD=${trDeployment.rejectSize}".toString(),
                "DEBUG=${trDeployment.debug}".toString()
        ]

        def deployments = []
        trDeployment.deployments.each {
            def deployment = [
                    NAME    : "${it.softwareSystem.name}".toString(),
                    URL     : "${it.connection.url}".toString(),
                    USER    : "${it.connection.user}".toString(),
                    DRIVER  : "${it.connection.driver}".toString(),
                    PASSWORD: "${JdbcConnection.getPassword(it.connection)}".toString(),
                    SCHEMA  : "${it.connection.schema}".toString()
            ]
            deployments.add(deployment)
        }
        trDeployment.parameters.each {
            jobParams.add(JSONHelper.escape("${it.name}=${it.value}").toString())
        }
        trDeployment.sparkConf.each {
            jobParams.add(JSONHelper.escape("${it.name}=${it.value}").toString())
        }

        Path deployDir = java.nio.file.Files.createTempDirectory("dg")
        try {
            Path file = deployDir.resolve("${trDeployment.name}/${transformation.name}Part.scala")
            Context.current.getContextSvc().epsilonSvc.executeEgx("/psm/etl/spark/TransformationPart.egx", [mspaceRoot: deployDir.toUri().toString(), jobDeployment: trDeployment, nodeName: params.nodeName, outputType: params.outputType, jobParams: jobParams, deployments: deployments, sampleSize: params.sampleSize, statement: params.statement], [emfModel])
            return [result: true, fileContent: new String(readAllBytes(file))]
        } finally {
            GitflowSvc.deleteDirectoryRecursive(deployDir)
        }
        /* protected region MetaServer.rtTransformationDeployment.generatePart end */
    }

    static Object build(Map entity, Map params = null) {
        /* protected region MetaServer.rtTransformationDeployment.build on begin */
        if (params.noBuild != true) {
            def trDeployment = new Database("teneo").get("rt.TransformationDeployment", (Long) entity.e_id)
            def transformation = trDeployment.transformation
            if(isNeedRebuild(trDeployment)){
                def result = chain([
                        Transformation.&generate,
                        Transformation.&build
                ], transformation, params)
            }
        }
        return [result: true, problems: []]
        /* protected region MetaServer.rtTransformationDeployment.build end */
    }

    static Object deploy(Map entity, Map params = null) {
        /* protected region MetaServer.rtTransformationDeployment.deploy on begin */
        def trDeployment = new Database("teneo").get("rt.TransformationDeployment", (Long) entity.e_id)
        def livyServer = trDeployment.livyServer
        def hdfs = new HDFSClient(livyServer.webhdfs, livyServer.user, livyServer)

        def path = "${livyServer.home}/${livyServer.user}/deployments/${trDeployment.name}"

        hdfs.deleteDir(path)
        hdfs.createDir(path)
        def gitFlow = Context.current.getContextSvc().getGitflowSvc()
        return gitFlow.inGitTransaction(null, new Callable<Map>() {
            @Override
            Map call() throws Exception {
                def buildVersion = trDeployment.transformation.buildVersion ? trDeployment.transformation.buildVersion : "1.0-SNAPSHOT"
                def job = gitFlow.getCurrentGfs().getRootPath().resolve(gitFlow.SOURCES + "/Transformation/${trDeployment.transformation.name}/target/ru.neoflex.meta.etl2.spark.${trDeployment.transformation.name}-${buildVersion}.jar")
                hdfs.putBytes("${path}/${job.getFileName().toString()}", java.nio.file.Files.readAllBytes(job))
                def config = gitFlow.getCurrentGfs().getRootPath().resolve(gitFlow.SOURCES + "/TransformationDeployment/${trDeployment.name}/${trDeployment.transformation.name}.json")
                hdfs.putBytes("${path}/${config.getFileName().toString()}", java.nio.file.Files.readAllBytes(config))
                return [result: true, problems: []]
            }
        })
        /* protected region MetaServer.rtTransformationDeployment.deploy end */
    }


    static isNeedRebuild(Map trDeployment){
        def changeDateTime = getMaxUpdated([trDeployment, trDeployment.transformation])
        def transformation = trDeployment.transformation
        def gitFlow = Context.current.getContextSvc().getGitflowSvc()
        return gitFlow.inGitTransaction(null, new Callable<Boolean>() {
            @Override
            Boolean call() throws Exception {
                def status = gitFlow.getCurrentGfs().getRootPath().resolve(gitFlow.SOURCES + "/Transformation/${transformation.name}/status.txt")
                Date lastModified = gitFlow.getLastModified(status)
                if (lastModified == null || !java.nio.file.Files.isRegularFile(status) || changeDateTime.time > lastModified.time) {
                    return true
                }
                return false
            }
        })
    }

    static Object install(Map entity, Map params = null) {
        /* protected region MetaServer.rtTransformationDeployment.install on begin */
        Database db = Database.new
        Map td = db.get(entity)
        def transformation = td.transformation

        if (isNeedRebuild(td)) {
            def result = chain([
                    TransformationDeployment.&generate,
                    TransformationDeployment.&build
            ], entity, params)
            if (result.result) {
                def gitFlow = Context.current.getContextSvc().getGitflowSvc()
                gitFlow.inGitTransaction("install ${transformation.name}", new Callable<Void>() {
                    @Override
                    Void call() throws Exception {
                        def status = gitFlow.getCurrentGfs().getRootPath().resolve(gitFlow.SOURCES + "/Transformation/${transformation.name}/status.txt")
                        java.nio.file.Files.createDirectories(status.getParent())
                        java.nio.file.Files.write(status, "OK".bytes)
                        return null
                    }
                })
            }
            if (!result.result) {
                logger.error(result.problems.toString())
            }
            return result
        }
        return [result: true, problems: []]
        /* protected region MetaServer.rtTransformationDeployment.install end */
    }

    static Object run(Map entity, Map params = null) {
        /* protected region MetaServer.rtTransformationDeployment.run on begin */
        def trDeployment = Database.new.get(entity)
        def transformation = trDeployment.transformation
        def livyServer
        if (params.livyServer != null) livyServer = params.livyServer
        else if (trDeployment.livyServer != null) livyServer = trDeployment.livyServer
        if (trDeployment.master == null || trDeployment.master == '') throw new RuntimeException("master for TransformationDeployment " + trDeployment.name + " is empty")

        LinkedHashMap<String, Object> batchParams = getBatchParams(trDeployment, transformation, params)
        def sessionId
        def result = LivyServer.createBatchSession(livyServer, batchParams).result
        try {
            sessionId = result.id
        } catch (e) {
            throw new RuntimeException(result)
        }
        logger.info("Transformation submitted")
        logger.info("Transformation name:       ${transformation.name}")
        logger.info("Transformation deployment: ${trDeployment.name}")
        logger.info("Livy:                      ${livyServer.name}")
        logger.info("Batch Id:                ${sessionId}")

        return [result: true, problems: [], data: [sessionId: sessionId]]

        /* protected region MetaServer.rtTransformationDeployment.run end */
    }

    static LinkedHashMap<String, Object> getBatchParams(Map trDeployment, transformation, Map params) {
        def livyServer = trDeployment.livyServer
        def buildVersion = transformation.buildVersion ? transformation.buildVersion : "1.0-SNAPSHOT"
        def path = "${livyServer.home}/${livyServer.user}/deployments/${trDeployment.name}/ru.neoflex.meta.etl2.spark.${transformation.name}-${buildVersion}.jar".toString()
        def workflowId = "${transformation.name}_${System.identityHashCode([])}"
        def jobParams = [
                "HOME=${livyServer.home}".toString(),
                "USER=${livyServer.user}".toString(),
                "WF_HOME=${livyServer.home}/${livyServer.user}".toString(),
                "ROOT_WORKFLOW_ID=${workflowId}".toString(),
                "CURRENT_WORKFLOW_ID=${workflowId}".toString(),
                "SLIDE_SIZE=${trDeployment.slideSize}".toString(),
                "FETCH_SIZE=${trDeployment.fetchSize}".toString(),
                "PARTITION_NUM=${trDeployment.partitionNum}".toString(),
                "FAIL_THRESHOLD=${trDeployment.rejectSize}".toString(),
                "DEBUG=${trDeployment.debug}".toString(),
                "MASTER=${trDeployment.master}".toString()
        ]

        trDeployment.deployments.each {
            jobParams.add("JDBC_${it.softwareSystem.name}_URL=${it.connection.url}".toString())
            jobParams.add("JDBC_${it.softwareSystem.name}_USER=${it.connection.user}".toString())
            jobParams.add("JDBC_${it.softwareSystem.name}_DRIVER=${it.connection.driver}".toString())
            jobParams.add("JDBC_${it.softwareSystem.name}_PASSWORD=${SymmetricCipher.encrypt(JdbcConnection.getPassword(it.connection))}".toString())
            jobParams.add("JDBC_${it.softwareSystem.name}_SCHEMA=${it.connection.schema}".toString())
        }
        def parameters = [:]
        trDeployment.parameters.each {
            parameters.put(it.name, it.value)
        }
        parameters.putAll(params)
        for (key in parameters.keySet()) {
            jobParams.add(JSONHelper.escape("${key}=${parameters[key]}").toString())
        }
        def batchParams = [file: path, proxyUser: "${livyServer.user}".toString(), className: "ru.neoflex.meta.etl2.spark.${transformation.name}Job".toString(), args: jobParams]
        if (trDeployment.driverMemory) batchParams["driverMemory"] = trDeployment.driverMemory
        if (trDeployment.executorMemory) batchParams["executorMemory"] = trDeployment.executorMemory
        if (trDeployment.executorCores) batchParams["executorCores"] = trDeployment.executorCores
        if (trDeployment.numExecutors) batchParams["numExecutors"] = trDeployment.numExecutors
        if(trDeployment.sparkConf != null && trDeployment.sparkConf.size() > 0) {
            def sparkConf = [:]
            for(param in trDeployment.sparkConf) {
                sparkConf.putAt(param.name, param.value)
            }
            batchParams.conf = sparkConf
        }
        batchParams
    }

    static Object runPart(Map entity, Map params = null) {
        /* protected region MetaServer.rtTransformationDeployment.runPart on begin */
        def trDeployment = Database.new.get(entity)
        def livyServer = LivyServer.findCurrentLivyServer(trDeployment, params)
        def sessionId = LivyServer.getSessionId(params, livyServer)
        def result = LivyServer.executeStatementAndWait(sessionId, (String) params.code, logger, livyServer)
        return LivyServer.parseResult(result, params.outputType, sessionId)
        /* protected region MetaServer.rtTransformationDeployment.runPart end */
    }

    static Object generateAndRun(Map entity, Map params = null) {
        /* protected region MetaServer.rtTransformationDeployment.generateAndRun on begin */

        return chain([
                TransformationDeployment.&install,
                TransformationDeployment.&deploy,
                TransformationDeployment.&run,
                TransformationDeployment.&waitJob
        ], entity, params)
        /* protected region MetaServer.rtTransformationDeployment.generateAndRun end */
    }

    static Object buildAndRun(Map entity, Map params = null) {
        /* protected region MetaServer.rtTransformationDeployment.buildAndRun on begin */
        return chain([
                TransformationDeployment.&build,
                TransformationDeployment.&deploy,
                TransformationDeployment.&run,
                TransformationDeployment.&waitJob
        ], entity, params)
        /* protected region MetaServer.rtTransformationDeployment.buildAndRun end */
    }
}
