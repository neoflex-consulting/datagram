package MetaServer.jitdataset;
import ru.neoflex.meta.utils.Context;
/* protected region MetaServer.jitdatasetDeploymentDescriptor.inport on begin */
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import ru.neoflex.meta.model.Database;
/* protected region MetaServer.jitdatasetDeploymentDescriptor.inport end */
class DeploymentDescriptor {
    /* protected region MetaServer.jitdatasetDeploymentDescriptor.statics on begin */
    private final static Log logger = LogFactory.getLog(DeploymentDescriptor.class);

    /* protected region MetaServer.jitdatasetDeploymentDescriptor.statics end */

    public static Object activate(Map entity, Map params = null) {
    /* protected region MetaServer.jitdatasetDeploymentDescriptor.activate on begin */
        def db = Database.new
        def deployment = db.get(entity)
        deployment.datasets.each {
            Dataset.activateInt(it, deployment)
        }
        return [result: true, problems: []]
    /* protected region MetaServer.jitdatasetDeploymentDescriptor.activate end */
    }
}
