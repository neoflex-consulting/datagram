package MetaServer.rt

import java.util.Map

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.JSONHelper
import ru.neoflex.meta.utils.Context
import org.eclipse.epsilon.emc.emf.EmfModel
import org.eclipse.epsilon.etl.EtlModule
import MetaServer.etl.Project
import MetaServer.utils.EMF

class ImportWizard {
    private final static Log logger = LogFactory.getLog(Environment.class);
    public static Object loadMetadata(Map entity, Map params = null) {
        def importWizard = Database.new.get(entity)
        def jdbcContext = importWizard.jdbcContext
        if (jdbcContext == null) {
            throw new RuntimeException("jdbcContext not defined")
        }
        def softwareSystems = Database.new.select("from rt.SoftwareSystem where name=:name", [name: jdbcContext.name])
        if (softwareSystems.size() != 1) {
            throw new RuntimeException("SoftwareSystem not found or not unique")
        }
        def softwareSystem = softwareSystems.get(0)
        def deployment = softwareSystem.defaultDeployment
        if (deployment == null) {
            throw new RuntimeException("defaultDeployment not defined")
        }
        def connection = deployment.connection
        if (connection == null) {
            throw new RuntimeException("connection not defined")
        }
        def conn = JdbcConnection.getConnection(connection)
        try {
            def catalogs = []
            def catSet = conn.getMetaData().getCatalogs()
            try {
                while (catSet.next()) {
                    def tableCat = catSet.getString("TABLE_CAT")
                    catalogs.add(tableCat)
                }
            }
            finally {
                catSet.close()
            }
            def tables = []
            def created = 0
            def deleted = 0
            String[] types = ["TABLE", "VIEW"]
            def tabSet = conn.getMetaData().getTables(null, null, "%", types)
            try {
                while (tabSet.next()) {
                    def TABLE_CAT = tabSet.getString("TABLE_CAT")
                    def TABLE_SCHEM = tabSet.getString("TABLE_SCHEM")
                    def TABLE_NAME = tabSet.getString("TABLE_NAME")
                    def TABLE_TYPE = tabSet.getString("TABLE_TYPE")
                    def DESCRIPTION = tabSet.getString("REMARKS")
                    def importEntity = importWizard.entities.find {it.name == TABLE_NAME && it.schema == TABLE_SCHEM}
                    if (importEntity == null) {
                        def importEntityType = JSONHelper.getEnumerator("teneo", "rt.ImportEntity", "importEntityType", TABLE_TYPE)
                        importEntity = Database.new.instantiate("rt.ImportEntity", [
                                name: TABLE_NAME,
                                schema: TABLE_SCHEM,
                                importEntityType: importEntityType,
                                active: true
                        ])
                        importWizard.entities.add(importEntity)
                        created += 1
                    }
                    tables.add([TABLE_CAT: TABLE_CAT, TABLE_SCHEM: TABLE_SCHEM, TABLE_NAME: TABLE_NAME, TABLE_TYPE: TABLE_TYPE])
                }
            }
            finally {
                tabSet.close()
            }
            (importWizard.entities as List).forEach {e->
                if (tables.every {it.TABLE_NAME != e.name && it.TABLE_SCHEM != e.schema}) {
                    e.deleted = true
                    deleted += 1
                }
            }
            if (created > 0 || deleted > 0) {
                Database.new.save(importWizard)
            }
            return [created: created, deleted: deleted]
        }
        finally {
            conn.close()
        }
    }
    
    private static void clear(name) {
        def db = new Database("teneo")
        def wfName = name + "_workflow";
        def wdName = name + "_WorkflowDeployment";
        def existingWorkflow = db.select("from etl.Workflow where name=:name", [name: wfName])
        def existingWDeployment = db.select("from rt.WorkflowDeployment where name=:name", [name: wdName])
        
        if(existingWDeployment.size > 0) {
            db.delete("rt.WorkflowDeployment", existingWDeployment.get(0))
        }
        if(existingWorkflow.size > 0) {
            db.delete("etl.Workflow", existingWorkflow.get(0))
        }
        Context.current.savepoint();
    }
    
    public static Object generateWorkflow(Map entity, Map params = null) {
        /* protected region MetaServer.ImportWizard.genarateWorkflow on begin */
                clear(entity.name)
                def db = new Database("teneo")
                def Map importWizard = db.get(entity)
                def defaultDeployment = null
                if(importWizard.jdbcContext != null) {
                    def softwareSystemList = db.select("from rt.SoftwareSystem where name=:name", [name: importWizard.jdbcContext.name])
                    if(softwareSystemList.size() > 0) {
                        defaultDeployment = softwareSystemList[0].defaultDeployment
                    }
                }
                EtlModule module = new EtlModule();

                EmfModel source = new EmfModel();
                source.modelFileUri = org.eclipse.emf.common.util.URI.createURI("hibernate://?dsname=teneo&query1=from ${entity._type_} where e_id=${entity.e_id}")
                source.setName("S");
                source.setReadOnLoad(true);
                source.setStoredOnDisposal(false);
                source.setExpand(true);
                source.setMetamodelUris(["http://www.neoflex.ru/meta/rt", "http://www.neoflex.ru/meta/etl"]);
                source.loadModelFromUri();
               
                EmfModel target = new EmfModel();
                target.modelFileUri = org.eclipse.emf.common.util.URI.createURI("hibernate://?dsname=teneo")
                target.setName("T");
                target.setReadOnLoad(false);
                target.setStoredOnDisposal(true);
                target.setExpand(false);
                target.setMetamodelUris(["http://www.neoflex.ru/meta/etl", "http://www.neoflex.ru/meta/rt"]);
                target.loadModelFromUri();
                def models = [source, target]

                Context.current.contextSvc.epsilonSvc.execute(module, Thread.currentThread().getContextClassLoader().getResource("pim/rt/importWizard2workflow.etl").toURI(), [defaultDeployment:defaultDeployment], models, true);
                Project.setJsonView(entity.project)

                return ["Hello from MetaServer.rt.ImportWizard.execute"]
        /* protected region MetaServer.ImportWizard.genarateWorkflow end */
        }

}
