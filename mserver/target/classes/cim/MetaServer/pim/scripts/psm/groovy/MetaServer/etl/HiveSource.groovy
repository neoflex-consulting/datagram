package MetaServer.etl;
import ru.neoflex.meta.utils.Context;
/* protected region MetaServer.etlHiveSource.inport on begin */
import MetaServer.utils.JDBC
/* protected region MetaServer.etlHiveSource.inport end */
class HiveSource {
    /* protected region MetaServer.etlHiveSource.statics on begin */
    /* protected region MetaServer.etlHiveSource.statics end */

    public static Object execute(Map entity, Map params = null) {
    /* protected region MetaServer.etlHiveSource.execute on begin */
    	Integer sampleSize = 0;
    	if (entity.sampleSize != null) {
    		sampleSize = entity.sampleSize
    	}
        return JDBC.execute(Context.current, entity.context.name, entity.statement, sampleSize)
    /* protected region MetaServer.etlHiveSource.execute end */
    }
}
