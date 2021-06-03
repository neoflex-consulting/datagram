package MetaServer.etl;
import ru.neoflex.meta.utils.Context;
/* protected region MetaServer.etlTableSource.inport on begin */
import MetaServer.utils.JDBC;
/* protected region MetaServer.etlTableSource.inport end */
class TableSource {
    /* protected region MetaServer.etlTableSource.statics on begin */
    /* protected region MetaServer.etlTableSource.statics end */

    public static Object execute(Map entity, Map params = null) {
    /* protected region MetaServer.etlTableSource.execute on begin */
        return JDBC.execute(Context.current, entity.context.name, "select * from ${entity.name}", entity.sampleSize)
    /* protected region MetaServer.etlTableSource.execute end */
    }
}
