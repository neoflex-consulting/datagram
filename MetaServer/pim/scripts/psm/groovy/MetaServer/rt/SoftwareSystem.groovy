package MetaServer.rt;
import ru.neoflex.meta.utils.Context;
/* protected region MetaServer.rtSoftwareSystem.inport on begin */
import ru.neoflex.meta.model.Database;
/* protected region MetaServer.rtSoftwareSystem.inport end */
class SoftwareSystem {
    /* protected region MetaServer.rtSoftwareSystem.statics on begin */
    /* protected region MetaServer.rtSoftwareSystem.statics end */

    public static Object refreshScheme(Map entity, Map params = null) {
    /* protected region MetaServer.rtSoftwareSystem.refreshScheme on begin */
        def softwareSystem = Database.new.get("rt.SoftwareSystem", (Long)entity.e_id)
        if (softwareSystem.defaultDeployment == null) {
            throw new RuntimeException("No default deployment for rt.SoftwareSystem ${softwareSystem.name}")
        }
        return Deployment.refreshScheme(softwareSystem.defaultDeployment)
    /* protected region MetaServer.rtSoftwareSystem.refreshScheme end */
    }
}
