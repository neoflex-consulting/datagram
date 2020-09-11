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

import java.nio.file.Paths
/* protected region MetaServer.rtWorkflowDeployment.inport end */
class WorkflowDeployment extends GenerationBase {
    /* protected region MetaServer.rtWorkflowDeployment.statics on begin */
    private final static Log logger = LogFactory.getLog(WorkflowDeployment.class);

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
        toCollect.findAll {it != null}.each { wf ->
            if (!collected.any {it.e_id == wf.e_id}) {
                collected.add(wf);
                wf.nodes.findAll {it._type_ == "etl.WFSubWorkflow" && it.subWorkflow != null}.each {collectWorkflows([it.subWorkflow], collected)}
            }
        }
        return collected
    }

    public static List getWorkflows(jobDeployment) {
        return collectWorkflows(([jobDeployment.start] + jobDeployment.workflows).unique { wf -> wf?.e_id}, [])
    }

    public static Object validateModel(Map entity) {
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

    public static Object validateScripts(Map entity) {
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

    public static Object validate(Map entity, Map params = null) {
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

    public static Object generate(Map entity, Map params = null) {
    /* protected region MetaServer.rtWorkflowDeployment.generate on begin */
        def retModel = validateModel(entity)
        if (!retModel.result) {
            for (problem in retModel.problems) {
                logger.error(problem)
            }
            return retModel
        }
        def deploymentDir = getSourcesDirectoryPath("WorkflowDeployment")
        def transformationDir = getSourcesDirectoryPath("Transformation")

        def jobDeployment = new Database("teneo").get("rt.JobDeployment", (Long)entity.e_id)
        if (jobDeployment.oozie == null) {
            throw new RuntimeException("Oozie not found in WorkflowDeployment ${jobDeployment.name}")
        }
        FileSystem.forceDeleteFolder(Paths.get("${deploymentDir}/${jobDeployment.name}"))
        //if(true) return
        //if(true) return
        generateJobFile(jobDeployment, deploymentDir)
        def transformations = []
        for (workflow in getWorkflows(jobDeployment)) {
            def emfModel = new EmfModel();
            def properties = new StringProperties()
            properties.put(EmfModel.PROPERTY_NAME, "src")
            properties.put(EmfModel.PROPERTY_MODEL_URI, "hibernate://?dsname=teneo&query1=from etl.Workflow where e_id=${workflow.e_id}")
            properties.put(EmfModel.PROPERTY_METAMODEL_URI, "http://www.neoflex.ru/meta/etl")
            properties.put(EmfModel.PROPERTY_READONLOAD, "true")
            emfModel.load(properties, "" );
            Context.current.getContextSvc().epsilonSvc.executeEgx("/psm/etl/spark/workflow.egx", [mspaceRoot:"file:///" + deploymentDir, jobDeployment: jobDeployment], [emfModel])
            for (node in workflow.nodes) {
                if (node._type_ == "etl.WFTransformation") {
                    def transformation = node.transformation
                    println(transformation.name)
                    transformations += transformation
                }
            }
            def ts = transformations as Set
            for (transformation in ts) {

                def auditInfo = transformation.auditInfo;
                def changeDateTime = auditInfo.changeDateTime
                def transformationGenerateDate = new Date(new File(transformationDir + "/${transformation.name}", "pom.xml").lastModified())
                def transformationBuildDate = new Date()
                def transformationBuildJar = new File(transformationDir + "/${transformation.name}/target", "ru.neoflex.meta.etl2.spark.${transformation.name}-1.0-SNAPSHOT.jar")
                def transformationBuildExists = transformationBuildJar.exists();
                if(transformationBuildExists){
                    transformationBuildDate = new Date(transformationBuildJar.lastModified())
                }
                if(!transformationBuildExists || transformationGenerateDate.before(changeDateTime) || transformationBuildDate.before(changeDateTime)) {
                    def result = chain([
                            MetaServer.etl.Transformation.&generate,
                            MetaServer.etl.Transformation.&build
                    ], transformation, params)
                    println("Call generate for " + transformation.name)
                }

            }
        }
        return [result: true, problems:[]]
        /* protected region MetaServer.rtWorkflowDeployment.generate end */
    }

    private static void generateJobFile(jobDeployment, String deploymentDir) {
        def jobModel = new EmfModel();
        def modelProperties = new StringProperties()
        modelProperties.put(EmfModel.PROPERTY_NAME, "src")
        modelProperties.put(EmfModel.PROPERTY_MODEL_URI, "hibernate://?dsname=teneo&query1=from rt.JobDeployment where e_id=${jobDeployment.e_id}")
        modelProperties.put(EmfModel.PROPERTY_METAMODEL_URI, "http://www.neoflex.ru/meta/rt")
        modelProperties.put(EmfModel.PROPERTY_READONLOAD, "true")
        jobModel.load(modelProperties, "");
        Context.current.getContextSvc().epsilonSvc.executeEgx("/psm/etl/spark/job.egx", [mspaceRoot: "file:///" + deploymentDir], [jobModel])
    }

    public static Object build(Map entity, Map params = null) {
    /* protected region MetaServer.rtWorkflowDeployment.build on begin */
        def deploymentDir = getSourcesDirectoryPath("WorkflowDeployment")
        def transformationDir = getSourcesDirectoryPath("Transformation")
        def jobDeployment = new Database("teneo").get("rt.JobDeployment", (Long)entity.e_id)
        println(jobDeployment.name + " loaded")

        def wfDir = "${deploymentDir}/${jobDeployment.name}"
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
        def ts = transformations as Set
        for (transformation in ts) {
            def transDir = "${transformationDir}/${transformation.name}"
            def auditInfo = transformation.auditInfo;
            def changeDateTime = auditInfo.changeDateTime
            def transformationGenerateDate = new Date(new File(transformationDir + "/${transformation.name}", "pom.xml").lastModified())
            def transformationBuildDate = new Date()
            def transformationBuildJar = new File(transformationDir + "/${transformation.name}/target", "ru.neoflex.meta.etl2.spark.${transformation.name}-1.0-SNAPSHOT.jar")
            def transformationBuildExists = transformationBuildJar.exists();
            if(transformationBuildExists){
                transformationBuildDate = new Date(transformationBuildJar.lastModified())
            }
            if(!transformationBuildExists || transformationGenerateDate.before(changeDateTime) || !transformationBuildDate.before(changeDateTime)) {
                def result = chain([
                        MetaServer.etl.Transformation.&generate,
                        MetaServer.etl.Transformation.&build
                ], transformation, params)
                println("Call generate for " + transformation.name)
            }
            Context.current.getContextSvc().getMavenSvc().run(new File("${transDir}/pom.xml"), "clean,install", null, null, null, [:]);
            FileUtils.copyFile(
                    new File("${transDir}/target/ru.neoflex.meta.etl${sparkVer}.spark.${transformation.name}-1.0-SNAPSHOT.jar"),
                    new File("${wfDir}/job/lib/ru.neoflex.meta.etl${sparkVer}.spark.${transformation.name}-1.0-SNAPSHOT.jar")
            )
        }
        def jars = getWorkflows(jobDeployment).collectMany {Workflow.collectJarFiles(it, [])}
        File toDir = new File("${wfDir}/job/lib")
        jars.each {
            def resource = Context.current.contextSvc.applicationContext.getResource(it)
            def filename = resource.filename.replaceAll("[?].*\$", "")
            File toFile = new File(toDir, filename)
            FileUtils.copyURLToFile(resource.getURL(), toFile)
        }
        return [result: true, problems:[]]
        /* protected region MetaServer.rtWorkflowDeployment.build end */
    }

    public static Object deploy(Map entity, Map params = null) {
    /* protected region MetaServer.rtWorkflowDeployment.deploy on begin */
        def deployDir = Context.current.getContextSvc().getmSpaceSvc().getDeployDir().getAbsolutePath();
        def jobDeployment = new Database("teneo").get("rt.JobDeployment", (Long)entity.e_id)
        println(jobDeployment.name + " loaded")
        def oozie = jobDeployment.oozie
        def hdfs = new HDFSClient(oozie.webhdfs, oozie.user, oozie)

        def path = "${oozie.home}/${oozie.user}/deployments/${jobDeployment.name}"

        hdfs.deleteDir(path)
        hdfs.createDir(path)

        def jobDir = new File(getSourcesDirectoryPath("WorkflowDeployment") + "/${jobDeployment.name}/job")
        def exclude = ["pom-run.xml", "pom-moveto.xml"]
        hdfs.putDir(path, jobDir, exclude)

        return [result: true, problems:[]]
        /* protected region MetaServer.rtWorkflowDeployment.deploy end */
    }

    public static Object forceInstall(Map entity, Map params = null) {
        Database db = Database.new
        Map wd = db.get(entity)
        def deployDir = getSourcesDirectoryPath("WorkflowDeployment")
        File statusFile = new File(deployDir+ "/${wd.name}/status.txt")
        def result = chain([
                WorkflowDeployment.&generate,
                WorkflowDeployment.&build,
                WorkflowDeployment.&deploy
        ], entity, params)
        if (result.result) {
            statusFile.write("OK")
        }
        return result
    }

    public static Object install(Map entity, Map params = null) {
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
        def sourcesDir = getSourcesDirectoryPath("WorkflowDeployment")
        def jobDeployment = new Database("teneo").get("rt.JobDeployment", (Long)entity.e_id)
        generateJobFile(jobDeployment, sourcesDir)

        File statusFile = new File(sourcesDir, "${wd.name}/status.txt")
        if (changeDateTime == null || !statusFile.isFile() || changeDateTime.time > statusFile.lastModified()) {
            def result = chain([
                    WorkflowDeployment.&generate,
                    WorkflowDeployment.&build,
                    WorkflowDeployment.&deploy
            ], entity, params)
            if (result.result) {
                statusFile.write("OK")
            }
            return result
        }
        return [result: true, problems:[]]
    /* protected region MetaServer.rtWorkflowDeployment.install end */
    }

    public static Object run(Map entity, Map params = null) {
    /* protected region MetaServer.rtWorkflowDeployment.run on begin */
        def deployDir = Context.current.getContextSvc().getDeployDir().getAbsolutePath();
        def jobDeployment = new Database("teneo").get("rt.JobDeployment", (Long)entity.e_id)
        def sourcesDir = Context.current.getContextSvc().getGitflowSvc().getCurrentSourcesDir().getAbsolutePath() + "/WorkflowDeployment"

        String deploymentDir = "${sourcesDir}/${jobDeployment.name}";

        //current.getContextSvc().getMavenSvc().run(new File("${deploymentDir}/workflow/pom-run.xml"), "validate", null, null, null, [:]);
        Properties props = new Properties()
        File propFile = new File("${deploymentDir}/job/job.properties")
        File tempFile = File.createTempFile("datagram", ".properties")
        props.load(propFile.newDataInputStream())

		def date = new Date()
        def now = new Date(date.getTime() - TimeZone.getDefault().getOffset(date.getTime()))

        props.setProperty('nominal_time', now.format("yyyy-MM-dd'T'HH:mm'Z'"))
        props.store(tempFile.newWriter(), null)

        def oozie = jobDeployment.oozie
        if (oozie == null) {
            throw new RuntimeException("Oozie not found in WorkflowDeployment ${jobDeployment.name}")
        }
        def wfId = Oozie.submitWorkflow(oozie, tempFile, params)
        logger.info("Workflow submitted")
        logger.info("Workflow name:       ${jobDeployment.start.name}")
        logger.info("Workflow deployment: ${jobDeployment.name}")
        logger.info("Oozie:               ${oozie.name}")
        logger.info("Workflow Id:         ${wfId}")
        Context.current.commitResources()
        def numOfRetries = Integer.valueOf(params?.numOfRetries ?: "1000")
        String status = null
        while (numOfRetries > 0) {
            status = Oozie.getWorkflowStatus(oozie, wfId);
            if (status in ["SUCCEEDED", "KILLED", "FAILED"]) {
                logger.info("Workflow ${wfId} finished. Status ${status}");
                break
            }
            logger.debug("[${numOfRetries}] Workflow ${wfId}. Status ${status}");
            numOfRetries--;
            try {
                Thread.sleep(3000);
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
        tempFile.delete()
        return [result: status == "SUCCEEDED", problems:problems, status: status]
        /* protected region MetaServer.rtWorkflowDeployment.run end */
    }

    public static Object generateAndRun(Map entity, Map params = null) {
    /* protected region MetaServer.rtWorkflowDeployment.generateAndRun on begin */
        return chain([
                WorkflowDeployment.&install,
                WorkflowDeployment.&run
        ], entity, params)
    /* protected region MetaServer.rtWorkflowDeployment.generateAndRun end */
    }

    public static Object buildAndRun(Map entity, Map params = null) {
    /* protected region MetaServer.rtWorkflowDeployment.buildAndRun on begin */
        return chain([
                WorkflowDeployment.&build,
                WorkflowDeployment.&deploy,
                WorkflowDeployment.&run
        ], entity, params)
    /* protected region MetaServer.rtWorkflowDeployment.buildAndRun end */
    }
}
