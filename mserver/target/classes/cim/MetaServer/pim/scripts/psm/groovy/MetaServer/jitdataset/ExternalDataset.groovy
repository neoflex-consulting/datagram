package MetaServer.jitdataset;
import ru.neoflex.meta.utils.Context;
/* protected region MetaServer.jitdatasetExternalDataset.inport on begin */
import MetaServer.utils.JDBC
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.JSONHelper;
/* protected region MetaServer.jitdatasetExternalDataset.inport end */
class ExternalDataset {
    /* protected region MetaServer.jitdatasetExternalDataset.statics on begin */
    /* protected region MetaServer.jitdatasetExternalDataset.statics end */

    public static Object refreshScheme(Map entity, Map params = null) {
    /* protected region MetaServer.jitdatasetExternalDataset.refreshScheme on begin */
        def db = Database.new
        def dataset = db.get(entity)
        def result = JDBC.execute(Context.current, dataset.context.name, dataset.sqlExpr, new Integer(params.sampleSize ?: "10"))
        if (new Boolean(params.queryOnly ?: "false") == true) {
            return result
        }
        def columns = result.columns
        dataset.dataFields.clear()
        for (column in columns) {
            def dataDomain = JSONHelper.getEnumerator("teneo", "jitdataset.ScalarType", "dataType", column.dataType._type_.tokenize(".").last())
            def dataType = db.instantiate("jitdataset.ScalarType", [dataType: dataDomain])
            def dataField = db.instantiate("jitdataset.DsField", [name: column.name, isVisible: true, dataType: dataType])
            dataset.dataFields.add(dataField)
        }
        db.save(dataset)
        return result
    /* protected region MetaServer.jitdatasetExternalDataset.refreshScheme end */
    }
}
