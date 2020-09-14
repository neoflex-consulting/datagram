package MetaServer.rt

import groovy.json.JsonOutput
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import ru.neoflex.meta.utils.Context

class ScheduledTask {
    private final static Log logger = LogFactory.getLog(ScheduledTask.class)

    public static Object refreshScheduler(Map entity, Map params = null) {
        logger.info("Refreshing scheduler")
        def changes = Context.current.contextSvc.schedulingSvc.refreshScheduler()
        return changes
    }

    public static Object testTask(Map entity, Map params = null) {
        logger.info("Test scheduled task")
        logger.info("entity: " + JsonOutput.toJson(entity))
        logger.info("params: " + JsonOutput.toJson(params))
        //throw new Exception("!!!failfailfail!!!")
        return [status: "OK"]
    }
}
