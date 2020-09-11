package MetaServer.dwh;
import ru.neoflex.meta.utils.Context;
/* protected region MetaServer.dwhStagingArea.inport on begin */
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.Context
import org.eclipse.epsilon.emc.emf.EmfModel
import org.eclipse.epsilon.etl.EtlModule
import MetaServer.etl.Project;
/* protected region MetaServer.dwhStagingArea.inport end */
class StagingArea {
    /* protected region MetaServer.dwhStagingArea.statics on begin */
    /* protected region MetaServer.dwhStagingArea.statics end */

    public static Object genarateWorkflow(Map entity, Map params = null) {
    /* protected region MetaServer.dwhStagingArea.genarateWorkflow on begin */
    		Project.clear(entity.project)
    		EtlModule module = new EtlModule();
        	EmfModel source = new EmfModel();
            source.modelFileUri = org.eclipse.emf.common.util.URI.createURI("hibernate://?dsname=teneo&query1=from ${entity._type_} where e_id=${entity.e_id}")
            //target.setResource(EMFResource.getTeneoResource(Context.current.contextSvc.teneoSvc.hbds))
            source.setName("S");
            source.setReadOnLoad(true);
            source.setStoredOnDisposal(false);
            source.setExpand(true);
            source.setMetamodelUris(["http://www.neoflex.ru/meta/dwh"]);
            source.loadModelFromUri();
           
            EmfModel target = new EmfModel();
            target.modelFileUri = org.eclipse.emf.common.util.URI.createURI("hibernate://?dsname=teneo")
            //target.setResource(EMFResource.getTeneoResource(Context.current.contextSvc.teneoSvc.hbds))
            target.setName("T");
            target.setReadOnLoad(false);
            target.setStoredOnDisposal(true);
            target.setExpand(false);
            target.setMetamodelUris(["http://www.neoflex.ru/meta/etl", "http://www.neoflex.ru/meta/rt"]);
            target.loadModelFromUri();
            def models = [source, target]
            //module.getContext().setOriginalModel(source);
            //module.getContext().setMigratedModel(target);
            Context.current.contextSvc.epsilonSvc.execute(module, Thread.currentThread().getContextClassLoader().getResource("pim/dwh/dwh2workflow.etl").toURI(), [:], models, true);
            Project.setJsonView(entity.project)
            return ["Hello from MetaServer.etl.StagingArea.execute"]
    /* protected region MetaServer.dwhStagingArea.genarateWorkflow end */
    }
}
