package MetaServer.sse

import java.util.Map

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

class AnalyticWorkspace extends Workspace {
    private final static Log log = LogFactory.getLog(AnalyticWorkspace.class)
    
    public static Map copy(Map entity, Map params = null) {
        return Workspace.fullCopy(entity, params, entity.name, entity._type_, false)        
    }
}
