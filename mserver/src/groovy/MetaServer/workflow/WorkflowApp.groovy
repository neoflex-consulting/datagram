package MetaServer.workflow;
import ru.neoflex.meta.utils.Context;
/* protected region MetaServer.workflowWorkflowApp.inport on begin */
import ru.neoflex.meta.model.Database;
import org.eclipse.epsilon.common.util.StringProperties
import org.eclipse.epsilon.emc.emf.EmfModel
/* protected region MetaServer.workflowWorkflowApp.inport end */
class WorkflowApp {
    /* protected region MetaServer.workflowWorkflowApp.statics on begin */
    /* protected region MetaServer.workflowWorkflowApp.statics end */

    public static Object generate(Context current, Map entity) {
    /* protected region MetaServer.workflowWorkflowApp.generate on begin */
        def mspaceDir = current.getContextSvc().getmSpaceSvc().getEpsilonSvc().getMSpaceDir().getAbsolutePath();
        def fileName = mspaceDir + "/pim/workflow/workflow.egx"
        def emfModel = new EmfModel();
        def properties = new StringProperties()
        properties.put(EmfModel.PROPERTY_NAME, "src")
        properties.put(EmfModel.PROPERTY_MODEL_URI, "hibernate://?dsname=teneo&query1=from workflow.WorkflowApp where e_id=${entity.e_id}")
        properties.put(EmfModel.PROPERTY_METAMODEL_URI, "http://www.neoflex.ru/meta/workflow")
        properties.put(EmfModel.PROPERTY_READONLOAD, "true")
        emfModel.load(properties, "" );
        def result = current.getContextSvc().epsilonSvc.executeEgx(fileName, [mspaceRoot:"file:///" + mspaceDir], [emfModel])
        entity = new Database("teneo").get("workflow.WorkflowApp", (Long)entity.e_id)
        println(entity.name)
        return ["Hello from MetaServer.workflow.WorkflowApp.generate->" + result]
    /* protected region MetaServer.workflowWorkflowApp.generate end */
    }
}
