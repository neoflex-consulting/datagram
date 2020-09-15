package MetaServer.sse

import MetaServer.utils.JDBC
import com.google.common.base.Strings
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.eclipse.emf.ecore.util.EcoreUtil

import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.Context

import java.sql.DatabaseMetaData
import java.sql.ResultSet
import ru.neoflex.meta.utils.ECoreUtils;

class ModelPipelineWorkspace extends Workspace {
    private final static Log log = LogFactory.getLog(ModelPipelineWorkspace.class)

    public static Map toValidation(Map entity, Map params = null) {       
        
        def db = Database.new
        def modelForValidation = db.get(entity)
        return Workspace.fullCopy(entity, params, "validate" + modelForValidation.name, "sse.ValidateModelPipelineWorkspace", true)        
    }

    public static Map fullCopy(Map entity, Map params = null) {
        def db = Database.new
        def entityCopy = db.get(entity)
        return Workspace.fullCopy(entity, params, "copy_of_" + entityCopy.name, entityCopy._type_, false)
    }

    public static Object exportWorkspace(Map entity, Map params = null) {
        return Workspace.exportWorkspace(entity, params)
    }

    public static Object importWorkspace(Map entity, Map params = null) {
        return Workspace.importWorkspace(entity, params)
    }

}
