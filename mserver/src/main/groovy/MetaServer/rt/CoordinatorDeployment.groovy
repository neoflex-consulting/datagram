package MetaServer.rt

import MetaServer.etl.Workflow
import MetaServer.utils.FileSystem
import MetaServer.utils.HDFSClient
import MetaServer.utils.Oozie
import org.apache.commons.io.FileUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.eclipse.epsilon.common.util.StringProperties
import org.eclipse.epsilon.emc.emf.EmfModel
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.Context
/* protected region MetaServer.rtCoordinatorDeployment.inport on begin */
import ru.neoflex.meta.utils.MetaResource

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.function.BiFunction
import java.util.function.Function

/* protected region MetaServer.rtCoordinatorDeployment.inport end */
class CoordinatorDeployment {
    /* protected region MetaServer.rtCoordinatorDeployment.statics on begin */
    private final static Log logger = LogFactory.getLog(CoordinatorDeployment.class);

    public static Object chain(fs, Map entity, Map params = null) {
        def problems = []
        for (f in fs) {
            def ret = f(entity, params)
            problems += ret.problems
            if (!ret.result) return [result: false, problems: problems]
        }
        return [result: true, problems: problems]
    }

    public static List collectWorkflows(List toCollect, List collected) {
        toCollect.each { wf ->
            if (!collected.any {it.e_id == wf.e_id}) {
                collected.add(wf);
                wf.nodes.findAll {it._type_ == "etl.WFSubWorkflow" && it.subWorkflow != null}.each {collectWorkflows([it.subWorkflow], collected)}
            }
        }
        return collected
    }

    public static List getWorkflows(jobDeployment) {
        return collectWorkflows([jobDeployment.coordinator.action.workflow], [])
    }

    public static Object validateModel(Map entity) {
        /* protected region MetaServer.rtCoordinatorDeployment.validate on begin */
        def jobDeployment = new Database("teneo").get("rt.JobDeployment", (Long)entity.e_id)
        def problems = []
        for (workflow in getWorkflows(jobDeployment)) {
            def ret = Workflow.validateModel(workflow)
            problems += ret.problems
        }
        return [result: problems.find {it.isCritique == false} == null, problems: problems]
        /* protected region MetaServer.rtCoordinatorDeployment.validate end */
    }

    public static Object validateScripts(Map entity) {
        /* protected region MetaServer.rtCoordinatorDeployment.validate on begin */
        def jobDeployment = new Database("teneo").get("rt.JobDeployment", (Long)entity.e_id)
        def problems = []
        for (workflow in getWorkflows(jobDeployment)) {
            def ret = Workflow.validateScripts(workflow)
            problems += ret.problems
        }
        return [result: problems.find {it.isCritique == false} == null, problems: problems]
        /* protected region MetaServer.rtCoordinatorDeployment.validate end */
    }
    /* protected region MetaServer.rtCoordinatorDeployment.statics end */

    public static Object validate(Map entity, Map params = null) {
    /* protected region MetaServer.rtCoordinatorDeployment.validate on begin */
        def retModel = validateModel(entity)
        def retScripts = validateScripts(entity)
        def ret = [result: retModel.result && retScripts.result, problems: retModel.problems + retScripts.problems]
        println("Validation result: " + ret.result)
        for (problem in ret.problems) {
            println(problem)
        }
        return ret
        /* protected region MetaServer.rtCoordinatorDeployment.validate end */
    }

    public static Object generate(Map entity, Map params = null) {
    /* protected region MetaServer.rtCoordinatorDeployment.generate on begin */
        def retModel = validateModel(entity)
        if (!retModel.result) {
            for (problem in retModel.problems) {
                logger.error(problem)
            }
            return retModel
        }
        def jobDeployment = new Database("teneo").get("rt.JobDeployment", (Long)entity.e_id)
        def gitFlow = Context.current.getContextSvc().getGitflowSvc()
        gitFlow.inDir(gitFlow.SOURCES + "/CoordinatorDeployment", "Generate Coordinator Deployment", new BiFunction<Path, Path, Map>() {
            @Override
            Map apply(Path tmp, Path gitPath) {
                WorkflowDeployment.generateJobFile(jobDeployment, tmp.toUri().toString())
                def coordModel = new EmfModel();
                def modelProperties = new StringProperties()
                modelProperties.put(EmfModel.PROPERTY_NAME, "src")
                modelProperties.put(EmfModel.PROPERTY_MODEL_URI, "hibernate://?dsname=teneo&query1=from etl.CoJob where e_id=${jobDeployment.coordinator.e_id}")
                modelProperties.put(EmfModel.PROPERTY_METAMODEL_URI, "http://www.neoflex.ru/meta/etl")
                modelProperties.put(EmfModel.PROPERTY_READONLOAD, "true")
                coordModel.load(modelProperties, "" );
                Context.current.getContextSvc().epsilonSvc.executeEgx("/psm/etl/spark/coord.egx", [mspaceRoot:tmp.toUri().toString(), jobDeployment: jobDeployment], [coordModel])
                for (workflow in getWorkflows(jobDeployment)) {
                    def emfModel = new EmfModel()
                    def properties = new StringProperties()
                    properties.put(EmfModel.PROPERTY_NAME, "src")
                    properties.put(EmfModel.PROPERTY_MODEL_URI, "hibernate://?dsname=teneo&query1=from etl.Workflow where e_id=${workflow.e_id}")
                    properties.put(EmfModel.PROPERTY_METAMODEL_URI, "http://www.neoflex.ru/meta/etl")
                    properties.put(EmfModel.PROPERTY_READONLOAD, "true")
                    emfModel.load(properties, "" )
                    Context.current.getContextSvc().epsilonSvc.executeEgx("/psm/etl/spark/workflow.egx", [mspaceRoot: tmp.toUri().toString(), jobDeployment: jobDeployment], [emfModel])
                }
                gitFlow.copyContentRecursive(tmp.resolve(jobDeployment.name), gitPath.resolve(jobDeployment.name), true)
                return null
            }
        })
        def transformations = []
        for (workflow in getWorkflows(jobDeployment)) {
            for (node in workflow.nodes) {
                if (node._type_ == "etl.WFTransformation") {
                    def transformation = node.transformation
                    println(transformation.name)
                    transformations += transformation
                }
            }
            def ts = transformations as Set
            for (transformation in ts) {
                Boolean needRebuild = gitFlow.inGitTransaction(null, new Callable<Boolean>() {
                    @Override
                    Boolean call() throws Exception {
                        def auditInfo = transformation.auditInfo
                        def changeDateTime = auditInfo.changeDateTime
                        def transformationGenerateDate = gitFlow.getLastModified(gitFlow.getCurrentGfs().getRootPath().resolve(gitFlow.SOURCES + "/Transformation/${transformation.name}/pom.xml"))
                        def buildVersion = transformation.buildVersion ? transformation.buildVersion : "1.0-SNAPSHOT"
                        def transformationBuildDate = gitFlow.getLastModified(gitFlow.getCurrentGfs().getRootPath().resolve(gitFlow.SOURCES + "/Transformation/${transformation.name}/target/ru.neoflex.meta.etl2.spark.${transformation.name}-${buildVersion}.jar"))
                        return changeDateTime != null && (transformationGenerateDate == null || transformationBuildDate == null ||
                                transformationGenerateDate.before(changeDateTime) || transformationGenerateDate.before(changeDateTime))
                    }
                });
                if(needRebuild) {
                    def result = chain([
                            MetaServer.etl.Transformation.&generate,
                            MetaServer.etl.Transformation.&build
                    ], transformation as Map, params)
                    println("Call generate for " + transformation.name)
                }
            }
        }
        return [result: true, problems:[]]
        /* protected region MetaServer.rtCoordinatorDeployment.generate end */
    }

    public static Object build(Map entity, Map params = null) {
        def jobDeployment = new Database("teneo").get("rt.JobDeployment", (Long)entity.e_id)
        println(jobDeployment.name + " loaded")

        def sparkVer = ""
        if (jobDeployment.oozie.spark2) sparkVer = "2"
        def transformations = []
        for (workflow in getWorkflows(jobDeployment)) {
            for (node in workflow.nodes) {
                if (node._type_ == "etl.WFTransformation") {
                    def transformation = node.transformation
                    println(transformation.name)
                    transformations += transformation
                }
            }
        }
        def gitFlow = Context.current.getContextSvc().getGitflowSvc()
        def ts = transformations as Set
        for (transformation in ts) {
            def buildVersion = transformation.buildVersion ? transformation.buildVersion : "1.0-SNAPSHOT"
            Boolean needRebuild = gitFlow.inGitTransaction(null, new Callable<Boolean>() {
                @Override
                Boolean call() throws Exception {
                    def auditInfo = transformation.auditInfo
                    def changeDateTime = auditInfo.changeDateTime
                    def transformationGenerateDate = gitFlow.getLastModified(gitFlow.getCurrentGfs().getRootPath().resolve(gitFlow.SOURCES + "/Transformation/${transformation.name}/pom.xml"))
                    def transformationBuildDate = gitFlow.getLastModified(gitFlow.getCurrentGfs().getRootPath().resolve(gitFlow.SOURCES + "/Transformation/${transformation.name}/target/ru.neoflex.meta.etl2.spark.${transformation.name}-${buildVersion}.jar"))
                    return changeDateTime != null && (transformationGenerateDate == null || transformationBuildDate == null ||
                            transformationGenerateDate.before(changeDateTime) ||
                            transformationBuildDate.before(changeDateTime))
                }
            });
            if(needRebuild) {
                def result = chain([
                        MetaServer.etl.Transformation.&generate,
                        MetaServer.etl.Transformation.&build
                ], transformation as Map, params)
                println("Call generate for " + transformation.name)
            }
        }
        return gitFlow.inCopy(gitFlow.SOURCES + "/CoordinatorDeployment/${jobDeployment.name}", "", new Function<File, Map>() {
            @Override
            Map apply(File dir) {
                def libPath = dir.toPath().resolve("job/lib")
                gitFlow.deleteDirectoryRecursive(libPath)
                Files.createDirectories(libPath)
                for (transformation in ts) {
                    def buildVersion = transformation.buildVersion ? transformation.buildVersion : "1.0-SNAPSHOT"
                    println("Apply copy ts lib: " + transformation.name + ", copy path : " + gitFlow.SOURCES + "/Transformation/${transformation.name}/target/ru.neoflex.meta.etl${sparkVer}.spark.${transformation.name}-${buildVersion}.jar");
                    Files.write(
                            libPath.resolve("ru.neoflex.meta.etl${sparkVer}.spark.${transformation.name}-${buildVersion}.jar"),
                            Files.readAllBytes(gitFlow.getCurrentGfs().getRootPath().resolve(
                                    gitFlow.SOURCES + "/Transformation/${transformation.name}/target/ru.neoflex.meta.etl${sparkVer}.spark.${transformation.name}-${buildVersion}.jar")
                            )
                    )
                }
                def jars = getWorkflows(jobDeployment).collectMany {Workflow.collectJarFiles(it, [])}
                jars.each {
                    def resource = Context.current.contextSvc.applicationContext.getResource(it)
                    resource.inputStream.withCloseable {is ->
                        Files.copy(is, libPath.resolve(resource.filename.replaceAll("[?].*\$", "")))
                    }
                }
                return [result: true, problems:[]]
            }
        })
        return [result: true, problems:[]]
    }

    public static Object deploy(Map entity, Map params = null) {
    /* protected region MetaServer.rtCoordinatorDeployment.deploy on begin */
        def jobDeployment = new Database("teneo").get("rt.JobDeployment", (Long)entity.e_id)
        println(jobDeployment.name + " loaded")
        def oozie = jobDeployment.oozie
        def hdfs = new HDFSClient(oozie.webhdfs, oozie.user, oozie)

        def path = "${oozie.home}/${oozie.user}/deployments/${jobDeployment.name}"

        hdfs.deleteDir(path)
        hdfs.createDir(path)

        def gitFlow = Context.current.getContextSvc().getGitflowSvc()
        return gitFlow.inCopy(gitFlow.SOURCES + "/CoordinatorDeployment/${jobDeployment.name}/job", null, new Function<File, Map>() {
            @Override
            Map apply(File jobDir) {
                def exclude = ["pom-run.xml", "pom-moveto.xml"]
                hdfs.putDir(path, jobDir, exclude)
                return [result: true, problems:[]]
            }
        })
        /* protected region MetaServer.rtCoordinatorDeployment.deploy end */
    }

    public static Object install(Map entity, Map params = null) {
    /* protected region MetaServer.rtCoordinatorDeployment.install on begin */
        Database db = Database.new
        Map jobDeployment = db.get(entity)
        def changeDateTime = null
        if (jobDeployment.auditInfo != null) {
            changeDateTime = jobDeployment.auditInfo.changeDateTime
        }
        if (jobDeployment.coordinator.auditInfo != null) {
            if (jobDeployment.coordinator.auditInfo.changeDateTime != null) {
                if (changeDateTime == null || changeDateTime.time < jobDeployment.coordinator.auditInfo.changeDateTime.time) {
                    changeDateTime = jobDeployment.coordinator.auditInfo.changeDateTime
                }
            }
        }
        getWorkflows(jobDeployment).each {
            if (it.auditInfo != null) {
                if (changeDateTime == null || it.auditInfo.changeDateTime != null && it.auditInfo.changeDateTime.after(changeDateTime)) {
                    changeDateTime = it.auditInfo.changeDateTime
                }
            }
            Workflow.collectWfTransformations(it, []).each {
                if (it.auditInfo != null) {
                    if (changeDateTime == null || it.auditInfo.changeDateTime != null && it.auditInfo.changeDateTime.after(changeDateTime)) {
                        changeDateTime = it.auditInfo.changeDateTime
                    }
                }
            }
        }
        def gitFlow = Context.current.getContextSvc().getGitflowSvc()
        Date statustLastModified = gitFlow.inGitTransaction(null, new Callable<Date>() {
            @Override
            Date call() throws Exception {
                return gitFlow.getLastModified(gitFlow.getCurrentGfs().getRootPath().resolve(gitFlow.SOURCES + "/CoordinatorDeployment/${jobDeployment.name}/status.txt"))
            }
        })
        if (statustLastModified == null || changeDateTime.time > statustLastModified.time) {
            def result = chain([
                    CoordinatorDeployment.&generate,
                    CoordinatorDeployment.&build,
                    CoordinatorDeployment.&deploy
            ], entity, params)
            if (result.result) {
                gitFlow.inGitTransaction("Install", new Callable<Void>() {
                    @Override
                    Void call() throws Exception {
                        Files.write(gitFlow.getCurrentGfs().getRootPath().resolve(gitFlow.SOURCES + "/CoordinatorDeployment/${jobDeployment.name}/status.txt"), "OK".bytes)
                        return null
                    }
                })
            }
            return result
        }
        return [result: true, problems:[]]
    /* protected region MetaServer.rtCoordinatorDeployment.install end */
    }

    public static Object run(Map entity, Map params = null) {
        def jobDeployment = new Database("teneo").get("rt.JobDeployment", (Long)entity.e_id)
        def gitFlow = Context.current.getContextSvc().getGitflowSvc()
        Properties props = new Properties()
        gitFlow.inGitTransaction(null, new Callable<Void>() {
            @Override
            Void call() throws Exception {
                def path = gitFlow.getCurrentGfs().getPath("/${gitFlow.SOURCES}/CoordinatorDeployment/${jobDeployment.name}/job/job.properties")
                return Files.newInputStream(path).withCloseable {is -> return props.load(is)}
            }
        })
        def oozie = jobDeployment.oozie
        if (oozie == null) {
            throw new RuntimeException("Oozie not found in Coordinator JobDeployment ${jobDeployment.name}")
        }
        def coId = File.createTempFile("datagram", ".properties").with {tempFile ->
            try {
                def date = new Date()
                def now = new Date(date.getTime() - TimeZone.getDefault().getOffset(date.getTime()))
                props.setProperty('nominal_time', now.format("yyyy-MM-dd'T'HH:mm'Z'"))
                tempFile.newWriter().withCloseable {writer -> props.store(writer, null)}
                return Oozie.submitWorkflow(oozie, tempFile, params)
            }
            finally {
                tempFile.delete()
            }
        }

        logger.info("Coordinator Job submitted")
        logger.info("Coordinator Job: ${jobDeployment.name}")
        logger.info("Oozie:               ${oozie.name}")
        logger.info("Coordinator Job Id:         ${coId}")
        Context.current.commitResources()
        return [result: true, problems:[]]
    }

    public static Object generateAndRun(Map entity, Map params = null) {
    /* protected region MetaServer.rtCoordinatorDeployment.generateAndRun on begin */
        return chain([
                CoordinatorDeployment.&install,
                CoordinatorDeployment.&run
        ], entity, params)
    /* protected region MetaServer.rtCoordinatorDeployment.generateAndRun end */
    }

    public static Object buildAndRun(Map entity, Map params = null) {
    /* protected region MetaServer.rtCoordinatorDeployment.buildAndRun on begin */
        return chain([
                CoordinatorDeployment.&build,
                CoordinatorDeployment.&deploy,
                CoordinatorDeployment.&run
        ], entity, params)
    /* protected region MetaServer.rtCoordinatorDeployment.buildAndRun end */
    }

    public static Object getStatus(Map entity, Map params = null) {
    /* protected region MetaServer.rtCoordinatorDeployment.getStatus on begin */
        def teneo = new Database("teneo");
        def jobDeployment = teneo.get(entity)
        def oozie = jobDeployment.oozie
        def jobId = jobDeployment.jobId
        def status = Oozie.getWorkflowStatus(oozie, jobId);
        if (!["SUCCEEDED", "RUNNING"].contains(status)) {
            def message = Oozie.getWorkflowLog(oozie, jobId)
            logger.error(message)
            return [status: status, message: message]
        }
        logger.info("Workflow ${jobId} is ${status}");
        return [status: status]
    /* protected region MetaServer.rtCoordinatorDeployment.getStatus end */
    }
}
