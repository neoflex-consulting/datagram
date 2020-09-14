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

import java.nio.file.Paths
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
        def deployDir = Context.current.getContextSvc().getmSpaceSvc().getEpsilonSvc().getDeployDir().getAbsolutePath();
        def jobDeployment = new Database("teneo").get("rt.JobDeployment", (Long)entity.e_id)
        FileSystem.forceDeleteFolder(Paths.get("${deployDir}/deployments/${jobDeployment.name}"))
        def jobModel = new EmfModel();
        def modelProperties = new StringProperties()
        modelProperties.put(EmfModel.PROPERTY_NAME, "src")
        modelProperties.put(EmfModel.PROPERTY_MODEL_URI, "hibernate://?dsname=teneo&query1=from rt.JobDeployment where e_id=${jobDeployment.e_id}")
        modelProperties.put(EmfModel.PROPERTY_METAMODEL_URI, "http://www.neoflex.ru/meta/rt")
        modelProperties.put(EmfModel.PROPERTY_READONLOAD, "true")
        jobModel.load(modelProperties, "" );
        Context.current.getContextSvc().epsilonSvc.executeEgx("/psm/etl/spark/job.egx", [mspaceRoot:"file:///" + deployDir], [jobModel])
        def coordModel = new EmfModel();
       // def modelProperties = new StringProperties()
        modelProperties.put(EmfModel.PROPERTY_NAME, "src")
        modelProperties.put(EmfModel.PROPERTY_MODEL_URI, "hibernate://?dsname=teneo&query1=from etl.CoJob where e_id=${jobDeployment.coordinator.e_id}")
        modelProperties.put(EmfModel.PROPERTY_METAMODEL_URI, "http://www.neoflex.ru/meta/etl")
        modelProperties.put(EmfModel.PROPERTY_READONLOAD, "true")
        coordModel.load(modelProperties, "" );
        Context.current.getContextSvc().epsilonSvc.executeEgx("/psm/etl/spark/coord.egx", [mspaceRoot:"file:///" + deployDir, jobDeployment: jobDeployment], [coordModel])
        for (workflow in getWorkflows(jobDeployment)) {
            def emfModel = new EmfModel();
            def properties = new StringProperties()
            properties.put(EmfModel.PROPERTY_NAME, "src")
            properties.put(EmfModel.PROPERTY_MODEL_URI, "hibernate://?dsname=teneo&query1=from etl.Workflow where e_id=${workflow.e_id}")
            properties.put(EmfModel.PROPERTY_METAMODEL_URI, "http://www.neoflex.ru/meta/etl")
            properties.put(EmfModel.PROPERTY_READONLOAD, "true")
            emfModel.load(properties, "" );
            Context.current.getContextSvc().epsilonSvc.executeEgx("/psm/etl/spark/workflow.egx", [mspaceRoot:"file:///" + deployDir, jobDeployment: jobDeployment], [emfModel])
            for (node in workflow.nodes) {
                if (node._type_ == "etl.WFTransformation") {
                    def transformation = node.transformation
                    println(transformation.name)
                    //MetaResource.exportDir("psm/etl/spark/src", new File("${deployDir}/deployments/${jobDeployment.name}/${transformation.name}/src"))
                    MetaResource.exportDir("psm/etl/spark/src/main/resources", new File("${deployDir}/deployments/${jobDeployment.name}/${transformation.name}/src/main/resources"))
                    emfModel = new EmfModel();
                    properties = new StringProperties()
                    properties.put(EmfModel.PROPERTY_NAME, "src")
                    properties.put(EmfModel.PROPERTY_MODEL_URI, "hibernate://?dsname=teneo&query1=from etl.Transformation where e_id=${transformation.e_id}")
                    properties.put(EmfModel.PROPERTY_METAMODEL_URI, "http://www.neoflex.ru/meta/etl")
                    properties.put(EmfModel.PROPERTY_READONLOAD, "true")
                    emfModel.load(properties, "" );
                    Context.current.getContextSvc().epsilonSvc.executeEgx("/psm/etl/spark/Transformation.egx", [mspaceRoot:"file:///" + deployDir, jobDeployment: jobDeployment, workflow:workflow, packagePrefix:""], [emfModel])
                }
            }

        }
        return [result: true, problems:[]]
        /* protected region MetaServer.rtCoordinatorDeployment.generate end */
    }

    public static Object build(Map entity, Map params = null) {
    /* protected region MetaServer.rtCoordinatorDeployment.build on begin */
        def deployDir = Context.current.getContextSvc().getDeployDir().getAbsolutePath();
        def jobDeployment = new Database("teneo").get("rt.JobDeployment", (Long)entity.e_id)
        println(jobDeployment.name + " loaded")
        for (workflow in getWorkflows(jobDeployment)) {
            def wfDir = "${deployDir}/deployments/${jobDeployment.name}"
            for (node in workflow.nodes) {
                if (node._type_ == "etl.WFTransformation") {
                    def transformation = node.transformation
                    println(transformation.name)
                    def transDir = "${wfDir}/${transformation.name}"
                    Context.current.getContextSvc().getMavenSvc().run(new File("${transDir}/pom.xml"), "clean,install", null, null, null, [:]);
                    FileUtils.copyFile(
                            new File("${transDir}/target/ru.neoflex.meta.etl.spark.${transformation.name}-1.0-SNAPSHOT.jar"),
                            new File("${wfDir}/job/lib/ru.neoflex.meta.etl.spark.${transformation.name}-1.0-SNAPSHOT.jar")
                    )
                }
            }
        }
        return [result: true, problems:[]]
        /* protected region MetaServer.rtCoordinatorDeployment.build end */
    }

    public static Object deploy(Map entity, Map params = null) {
    /* protected region MetaServer.rtCoordinatorDeployment.deploy on begin */
        def deployDir = Context.current.getContextSvc().getmSpaceSvc().getDeployDir().getAbsolutePath();
        def jobDeployment = new Database("teneo").get("rt.JobDeployment", (Long)entity.e_id)
        println(jobDeployment.name + " loaded")
        String deploymentDir = "${deployDir}/deployments/${entity.get("name")}";
        def oozie = jobDeployment.oozie

        def hdfs = new HDFSClient(oozie.webhdfs, oozie.user, oozie)
        def path = "${oozie.home}/${oozie.user}/deployments/${jobDeployment.name}"
        
        hdfs.deleteDir(path)
        hdfs.createDir(path)

        def jobDir = new File(deployDir, "deployments/${jobDeployment.name}/job")
        def exclude = ["pom-run.xml", "pom-moveto.xml"]
        hdfs.putDir(path, jobDir, exclude)

        return [result: true, problems:[]]
        /* protected region MetaServer.rtCoordinatorDeployment.deploy end */
    }

    public static Object install(Map entity, Map params = null) {
    /* protected region MetaServer.rtCoordinatorDeployment.install on begin */
        Database db = Database.new
        Map coJob = db.get(entity)
        def changeDateTime = null
        if (coJob.auditInfo != null) {
            changeDateTime = coJob.auditInfo.changeDateTime
        }
        getWorkflows(coJob).each {
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
        def deployDir = Context.current.getContextSvc().getmSpaceSvc().getEpsilonSvc().getDeployDir().getAbsolutePath();
        File statusFile = new File(deployDir, "deployments/${coJob.name}/status.txt")
        if (changeDateTime == null || !statusFile.isFile() || changeDateTime.time > statusFile.lastModified()) {
            def result = chain([
                    CoordinatorDeployment.&generate,
                    CoordinatorDeployment.&build,
                    CoordinatorDeployment.&deploy
            ], entity, params)
            if (result.result) {
                statusFile.write("OK")
            }
            return result
        }
        return [result: true, problems:[]]
    /* protected region MetaServer.rtCoordinatorDeployment.install end */
    }

    public static Object run(Map entity, Map params = null) {
    /* protected region MetaServer.rtCoordinatorDeployment.run on begin */
        def deployDir = Context.current.getContextSvc().getDeployDir().getAbsolutePath();
        def teneo = new Database("teneo");
        def jobDeployment = teneo.get("rt.JobDeployment", (Long)entity.e_id)
        String deploymentDir = "${deployDir}/deployments/${jobDeployment.name}";
        //current.getContextSvc().getMavenSvc().run(new File("${deploymentDir}/workflow/pom-run.xml"), "validate", null, null, null, [:]);
        File propFile = new File("${deploymentDir}/job/job.properties")
        def oozie = jobDeployment.oozie
        def jobId = Oozie.submitWorkflow(oozie, propFile, params)
        logger.info("Workflow submitted, id: ${jobId}")
        jobDeployment.jobId = jobId
        teneo.save(jobDeployment)

        return [result: true, problems:[]]
        /* protected region MetaServer.rtCoordinatorDeployment.run end */
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
