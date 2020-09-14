package MetaServer.etl;
import ru.neoflex.meta.utils.Context;
/* protected region MetaServer.etlWorkflow.inport on begin */
import org.eclipse.epsilon.common.util.StringProperties
import org.eclipse.epsilon.emc.emf.EmfModel
import ru.neoflex.meta.model.Database;
import MetaServer.utils.MetaInfo;
import MetaServer.rt.WorkflowDeployment;
import MetaServer.utils.Oozie;
/* protected region MetaServer.etlWorkflow.inport end */
class Workflow {
    /* protected region MetaServer.etlWorkflow.statics on begin */
    public static List collectWfTransformations(Map wf, List collected) {
        wf.nodes.findAll {it._type_ == "etl.WFSubWorkflow" && it.subWorkflow != null}.each {collectWfTransformations(it.subWorkflow, collected)}
        wf.nodes.findAll {it._type_ == "etl.WFTransformation" && it.transformation != null}.each {collected.add(it.transformation)}
        return collected
    }

    public static Map findOrCreateWFD(name, Map workflow) {
        def db = Database.new
        def wfd = db.session.createQuery("from rt.WorkflowDeployment where name = :name").setParameter("name", name).uniqueResult()
        if (wfd == null) {
            println("create new workflow deployment ${name}")
            def project = workflow.project
            def oozie = Oozie.findByProject(project)
            def deployments = []
            collectWfTransformations(workflow, []).each {Transformation.collectTrDeployments(it, deployments)}
            wfd = db.instantiate("rt.WorkflowDeployment", [name: name, project: Project.findOrCreateProject(), oozie: oozie, start: workflow, deployments: deployments.unique {"${it._type_}|${it.e_id}"}, debug: true, slideSize: 400, rejectSize: 1000, fetchSize: 1000, partitionNum: 1, persistOnDisk: true, master: "local", numExecutors: 1, executorCores: 1])
            db.save(wfd)
        }
        else {
            def deployments = []
            collectWfTransformations(workflow, []).each {Transformation.collectTrDeployments(it, deployments)}
            def count = 0
            deployments.each { d->
                if (!wfd.deployments.any {it.name == d.name}) {
                    wfd.deployments.add(d)
                    count += 1
                }
            }
            if (count > 0) {
                Database.new.save(wfd)
            }
        }
        return wfd
    }

    public static Object validateModel(Map entity) {
        def fileName = "/pim/etl/workflow.evl"
        def emfModel = new EmfModel();
        def properties = new StringProperties()
        properties.put(EmfModel.PROPERTY_NAME, "src")
        properties.put(EmfModel.PROPERTY_MODEL_URI, "hibernate://?dsname=teneo&query1=from etl.Workflow where e_id=${entity.e_id}")
        properties.put(EmfModel.PROPERTY_METAMODEL_URI, "http://www.neoflex.ru/meta/etl")
        properties.put(EmfModel.PROPERTY_READONLOAD, "true")
        emfModel.load(properties, "" );
        def problems = []
        Context.current.getContextSvc().epsilonSvc.executeEvl(fileName, [:], [emfModel], problems)
        def workflow = new Database("teneo").get("etl.Workflow", (Long)entity.e_id)
        for (node in workflow.nodes) {
            if (node._type_ == "etl.WFTransformation" && node.transformation != null) {
                def ret = Transformation.validateModel(node.transformation)
                problems += ret.problems.collect{it + [_type_: node._type_, e_id: node.e_id]}
            }
            else if (node._type_ == "etl.WFSubWorkflow" && node.subWorkflow != null) {
                def ret = validateModel(node.subWorkflow)
                problems += ret.problems.collect{it + [_type_: node._type_, e_id: node.e_id]}
            }
        }
        return [result: problems.find {it.isCritique == false} == null, problems: problems]
    }

    public static Object validateScripts(Map entity) {
        def workflow = new Database("teneo").get("etl.Workflow", (Long)entity.e_id)
        def problems = []
        for (node in workflow.nodes) {
            if (node._type_ == "etl.WFTransformation") {
                if (node.transformation != null) {
                    def ret = Transformation.validateScripts(node.transformation)
                    problems += ret.problems.collect{it + [_type_: node._type_, e_id: node.e_id]}
                }
            }
            else if (node._type_ == "etl.WFSubWorkflow") {
                if (node.subWorkflow != null) {
                    def ret = validateScripts(node.subWorkflow)
                    problems += ret.problems.collect{it + [_type_: node._type_, e_id: node.e_id]}
                }
            }
        }
        def result = problems.find {it.isCritique == false} == null
        def ret = [result: result, problems: problems]
        return ret
    }

    public static List collectJarFiles(Map workflow, List seen) {
        seen.addAll(workflow.nodes.findAll {/*it._type_ == 'etl.WFJava' && */it.jarFiles != null}.collectMany {it.jarFiles})
        workflow.nodes.findAll {it._type_ == 'etl.WFSubWorkflow' && it.subWorkflow != null}.each {
            collectJarFiles(it.subWorkflow, seen)
        }
        return seen
    }

    public static Object importWorkflow(Map entity, Map params = null) {
        def workflow = Database.new.get(entity)
        def project = workflow.project
        if (project == null) {
            throw new RuntimeException("Project not found")
        }
        return Project.importProjectEntity(project, workflow)
    }

    public static Object exportWorkflow(Map entity, Map params = null) {
        def workflow = Database.new.get(entity)
        def project = workflow.project
        if (project == null) {
            throw new RuntimeException("Project not found")
        }
        def svnCommitMessage = params.svnCommitMessage
        return Project.exportProjectEntityWithDependentObjects(project, entity, svnCommitMessage)
    }
    /* protected region MetaServer.etlWorkflow.statics end */

    public static Object validate(Map entity, Map params = null) {
    /* protected region MetaServer.etlWorkflow.validate on begin */
        def retModel = validateModel(entity)
        def retScripts = validateScripts(entity)
        def ret = [result: retModel.result && retScripts.result, problems: retModel.problems + retScripts.problems]
        println("Validation result: " + ret.result)
        for (problem in ret.problems) {
            println(problem)
        }
        return ret
    /* protected region MetaServer.etlWorkflow.validate end */
    }

    public static Object dependencies(Map entity, Map params = null) {
    /* protected region MetaServer.etlWorkflow.dependencies on begin */
        def deps = MetaInfo.getAllDeps(entity).collect {[_type_: it._type_, name: it.name, e_id: it.e_id]}
        println(deps)
        return deps
    /* protected region MetaServer.etlWorkflow.dependencies end */
    }

    public static Object setJsonView(Map entity, Map params = null) {
    /* protected region MetaServer.etlWorkflow.setJsonView on begin */
        Project.setEntityJsonView(entity._type_, entity.e_id, true)
        return ["Hello from MetaServer.etl.Workflow.setJsonView"]
    /* protected region MetaServer.etlWorkflow.setJsonView end */
    }

    public static Object install(Map entity, Map params = null) {
    /* protected region MetaServer.etlWorkflow.install on begin */
        def wfd = findOrCreateWFD("autogenerated_wf_" + entity.name, Database.new.get(entity))
        Context.current.commit()
        return WorkflowDeployment.install(wfd, params)
    /* protected region MetaServer.etlWorkflow.install end */
    }

    public static Object runit(Map entity, Map params = null) {
    /* protected region MetaServer.etlWorkflow.runit on begin */
        def wfd = findOrCreateWFD("autogenerated_wf_" + entity.name, Database.new.get(entity))
        Context.current.commit()
        return WorkflowDeployment.generateAndRun(wfd, params)
    /* protected region MetaServer.etlWorkflow.runit end */
    }

    public static Object svnProps(Map entity, Map params = null) {
        def workflow = Database.new.get(entity)
        def project = workflow.project
        return Project.svnProps(project, workflow)
    }
}
