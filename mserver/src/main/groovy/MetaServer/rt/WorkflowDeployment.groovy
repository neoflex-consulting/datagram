package MetaServer.rt

import MetaServer.etl.Workflow
import MetaServer.utils.FileSystem
import MetaServer.utils.HDFSClient
import MetaServer.utils.Oozie
import MetaServer.utils.GenerationBase
import org.apache.commons.io.FileUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.eclipse.epsilon.common.util.StringProperties
import org.eclipse.epsilon.emc.emf.EmfModel
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.Context
/* protected region MetaServer.rtWorkflowDeployment.inport on begin */
import ru.neoflex.meta.utils.MetaResource

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.function.BiFunction
import java.util.function.Function

/* protected region MetaServer.rtWorkflowDeployment.inport end */
class WorkflowDeployment extends GenerationBase {
    /* protected region MetaServer.rtWorkflowDeployment.statics on begin */
    private final static Log logger = LogFactory.getLog(WorkflowDeployment.class)

    static Object chain(fs, Map entity, Map params = null) {
        def problems = []
        for (f in fs) {
            def ret = f(entity, params)
            problems += ret.problems
            if (!ret.result) return [result: false, problems: problems]
        }
        return [result: true, problems: problems]
    }

    static List collectWorkflows(List toCollect, List collected) {
        toCollect.findAll {it != null}.each { wf ->
            if (!collected.any {it.e_id == wf.e_id}) {
                collected.add(wf)
                wf.nodes.findAll {it._type_ == "etl.WFSubWorkflow" && it.subWorkflow != null}.each {collectWorkflows([it.subWorkflow], collected)}
            }
        }
        return collected
    }

    static List getWorkflows(jobDeployment) {
        return collectWorkflows(([jobDeployment.start] + jobDeployment.workflows).unique { wf -> wf?.e_id}, [])
    }

    static Object validateModel(Map entity) {
        /* protected region MetaServer.rtWorkflowDeployment.validate on begin */
        def jobDeployment = new Database("teneo").get("rt.JobDeployment", (Long)entity.e_id)
        def problems = []
        for (workflow in getWorkflows(jobDeployment)) {
            def ret = Workflow.validateModel(workflow)
            problems += ret.problems
        }
        return [result: problems.find {it.isCritique == false} == null, problems: problems]
        /* protected region MetaServer.rtWorkflowDeployment.validate end */
    }

    static Object validateScripts(Map entity) {
        /* protected region MetaServer.rtWorkflowDeployment.validate on begin */
        def jobDeployment = new Database("teneo").get("rt.JobDeployment", (Long)entity.e_id)
        def problems = []
        for (workflow in getWorkflows(jobDeployment)) {
            def ret = Workflow.validateScripts(workflow)
            problems += ret.problems
        }
        return [result: problems.find {it.isCritique == false} == null, problems: problems]
        /* protected region MetaServer.rtWorkflowDeployment.validate end */
    }
    /* protected region MetaServer.rtWorkflowDeployment.statics end */

    static Object validate(Map entity, Map params = null) {
    /* protected region MetaServer.rtWorkflowDeployment.validate on begin */
        def retModel = validateModel(entity)
        def retScripts = validateScripts(entity)
        def ret = [result: retModel.result && retScripts.result, problems: retModel.problems + retScripts.problems]
        println("Validation result: " + ret.result)
        for (problem in ret.problems) {
            println(problem)
        }
        return ret
        /* protected region MetaServer.rtWorkflowDeployment.validate end */
    }

    static Object generate(Map entity, Map params = null) {
    /* protected region MetaServer.rtWorkflowDeployment.generate on begin */
        def retModel = validateModel(entity)
        if (!retModel.result) {
            for (problem in retModel.problems) {
                logger.error(problem)
            }
            return retModel
        }
        def jobDeployment = new Database("teneo").get("rt.JobDeployment", (Long)entity.e_id)
        if (jobDeployment.oozie == null) {
            throw new RuntimeException("Oozie not found in WorkflowDeployment ${jobDeployment.name}")
        }

        def gitFlow = Context.current.getContextSvc().getGitflowSvc()
        gitFlow.inDir(gitFlow.SOURCES + "/WorkflowDeployment", "Generate Workflow Deployment", new BiFunction<Path, Path, Map>() {
            @Override
            Map apply(Path tmp, Path gitPath) {
                generateJobFile(jobDeployment, tmp.toUri().toString())
                gitFlow.copyContentRecursive(tmp.resolve(jobDeployment.name), gitPath.resolve(jobDeployment.name), true)
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
        /* protected region MetaServer.rtWorkflowDeployment.generate end */
    }

    private static void generateJobFile(jobDeployment, String deploymentUri) {
        def jobModel = new EmfModel()
        def modelProperties = new StringProperties()
        modelProperties.put(EmfModel.PROPERTY_NAME, "src")
        modelProperties.put(EmfModel.PROPERTY_MODEL_URI, "hibernate://?dsname=teneo&query1=from rt.JobDeployment where e_id=${jobDeployment.e_id}")
        modelProperties.put(EmfModel.PROPERTY_METAMODEL_URI, "http://www.neoflex.ru/meta/rt")
        modelProperties.put(EmfModel.PROPERTY_READONLOAD, "true")
        jobModel.load(modelProperties, "")
        Context.current.getContextSvc().epsilonSvc.executeEgx("/psm/etl/spark/job.egx", [mspaceRoot: deploymentUri], [jobModel])
    }

    static Object build(Map entity, Map params = null) {
    /* protected region MetaServer.rtWorkflowDeployment.build on begin */
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
                    return changeDateTime != null && (
                            transformationGenerateDate == null || transformationBuildDate == null ||
                                    transformationGenerateDate.before(changeDateTime) ||
                                    transformationBuildDate.before(changeDateTime)
                    )
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
        return gitFlow.inCopy(gitFlow.SOURCES + "/WorkflowDeployment/${jobDeployment.name}", "", new Function<File, Map>() {
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
        /* protected region MetaServer.rtWorkflowDeployment.build end */
    }

    static Object deploy(Map entity, Map params = null) {
    /* protected region MetaServer.rtWorkflowDeployment.deploy on begin */
        def deployDir = Context.current.getContextSvc().getmSpaceSvc().getDeployDir().getAbsolutePath()
        def jobDeployment = new Database("teneo").get("rt.JobDeployment", (Long)entity.e_id)
        println(jobDeployment.name + " loaded")
        def oozie = jobDeployment.oozie
        def hdfs = new HDFSClient(oozie.webhdfs, oozie.user, oozie)

        def path = "${oozie.home}/${oozie.user}/deployments/${jobDeployment.name}"

        hdfs.deleteDir(path)
        hdfs.createDir(path)

        def gitFlow = Context.current.getContextSvc().getGitflowSvc()
        return gitFlow.inCopy(gitFlow.SOURCES + "/WorkflowDeployment/${jobDeployment.name}/job", null, new Function<File, Map>() {
            @Override
            Map apply(File jobDir) {
                def exclude = ["pom-run.xml", "pom-moveto.xml"]
                hdfs.putDir(path, jobDir, exclude)
                return [result: true, problems:[]]
            }
        })
        /* protected region MetaServer.rtWorkflowDeployment.deploy end */
    }

    static Object forceInstall(Map entity, Map params = null) {
        Database db = Database.new
        Map wd = db.get(entity)
        def result = chain([
                WorkflowDeployment.&generate,
                WorkflowDeployment.&build,
                WorkflowDeployment.&deploy
        ], entity, params)
        if (result.result) {
            def gitFlow = Context.current.getContextSvc().getGitflowSvc()
            gitFlow.inGitTransaction("forceInstall", new Callable<Void>() {
                @Override
                Void call() throws Exception {
                    Files.write(gitFlow.getCurrentGfs().getRootPath().resolve(gitFlow.SOURCES + "/WorkflowDeployment/${wd.name}/status.txt"), "OK".bytes)
                    return null
                }
            })
        }
        return result
    }

    static Object install(Map entity, Map params = null) {
    /* protected region MetaServer.rtWorkflowDeployment.install on begin */
        Database db = Database.new
        Map wd = db.get(entity)
        def changeDateTime = null
        if (wd.auditInfo != null) {
            changeDateTime = wd.auditInfo.changeDateTime
        }
        getWorkflows(wd).each {
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
                return gitFlow.getLastModified(gitFlow.getCurrentGfs().getRootPath().resolve(gitFlow.SOURCES + "/WorkflowDeployment/${wd.name}/status.txt"))
            }
        })
        if (statustLastModified == null || changeDateTime.time > statustLastModified.time) {
            def result = chain([
                    WorkflowDeployment.&generate,
                    WorkflowDeployment.&build,
                    WorkflowDeployment.&deploy
            ], entity, params)
            if (result.result) {
                gitFlow.inGitTransaction("forceInstall", new Callable<Void>() {
                    @Override
                    Void call() throws Exception {
                        Files.write(gitFlow.getCurrentGfs().getRootPath().resolve(gitFlow.SOURCES + "/WorkflowDeployment/${wd.name}/status.txt"), "OK".bytes)
                        return null
                    }
                })
            }
            return result
        }
        return [result: true, problems:[]]
    /* protected region MetaServer.rtWorkflowDeployment.install end */
    }

    static Object run(Map entity, Map params = null) {
    /* protected region MetaServer.rtWorkflowDeployment.run on begin */
        def jobDeployment = new Database("teneo").get("rt.JobDeployment", (Long)entity.e_id)
        def gitFlow = Context.current.getContextSvc().getGitflowSvc()
        Properties props = new Properties()
        gitFlow.inGitTransaction(null, new Callable<Void>() {
            @Override
            Void call() throws Exception {
                def path = gitFlow.getCurrentGfs().getPath("/${gitFlow.SOURCES}/WorkflowDeployment/${jobDeployment.name}/job/job.properties")
                return Files.newInputStream(path).withCloseable {is -> return props.load(is)}
            }
        })
        def oozie = jobDeployment.oozie
        if (oozie == null) {
            throw new RuntimeException("Oozie not found in WorkflowDeployment ${jobDeployment.name}")
        }
        def wfId = File.createTempFile("datagram", ".properties").with {tempFile ->
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

        logger.info("Workflow submitted")
        logger.info("Workflow name:       ${jobDeployment.start.name}")
        logger.info("Workflow deployment: ${jobDeployment.name}")
        logger.info("Oozie:               ${oozie.name}")
        logger.info("Workflow Id:         ${wfId}")
        Context.current.commitResources()
        def numOfRetries = Integer.valueOf(params?.numOfRetries ?: "1000")
        String status = null
        while (numOfRetries > 0) {
            status = Oozie.getWorkflowStatus(oozie, wfId)
            if (status in ["SUCCEEDED", "KILLED", "FAILED"]) {
                logger.info("Workflow ${wfId} finished. Status ${status}")
                break
            }
            logger.debug("[${numOfRetries}] Workflow ${wfId}. Status ${status}")
            numOfRetries--
            try {
                Thread.sleep(3000)
            } catch (InterruptedException e) {
            }
        }
        def problems = []
        if (status == null) {
            def message = "Workflow timed out"
            logger.info(message)
            problems += [result: false, status: status, message: message]
        }
        else if (status != "SUCCEEDED") {
            def message = Oozie.getWorkflowLog(oozie, wfId)
            logger.error(message)
            problems += [result: false, status: status, message: message]
        }
        return [result: status == "SUCCEEDED", problems:problems, status: status]
        /* protected region MetaServer.rtWorkflowDeployment.run end */
    }

    static Object generateAndRun(Map entity, Map params = null) {
    /* protected region MetaServer.rtWorkflowDeployment.generateAndRun on begin */
        return chain([
                WorkflowDeployment.&install,
                WorkflowDeployment.&run
        ], entity, params)
    /* protected region MetaServer.rtWorkflowDeployment.generateAndRun end */
    }

    static Object buildAndRun(Map entity, Map params = null) {
    /* protected region MetaServer.rtWorkflowDeployment.buildAndRun on begin */
        return chain([
                WorkflowDeployment.&build,
                WorkflowDeployment.&deploy,
                WorkflowDeployment.&run
        ], entity, params)
    /* protected region MetaServer.rtWorkflowDeployment.buildAndRun end */
    }
}
