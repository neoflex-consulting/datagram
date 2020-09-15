package MetaServer.sse

import MetaServer.utils.EMF
import ru.neoflex.meta.model.Database

class HiveExternalDataset extends HiveDataset {
    HiveExternalDataset() {
        super()
    }

    HiveExternalDataset(Map entity) {
        super(entity)
    }

    static Object fetchData(Map entity, Map params = null) {
        return HiveDataset.fetchData(entity, params)
    }

    static Object updateDsMetadata(Map entity, Map params = null) {
        return HiveDataset.updateDsMetadata(entity, params)
    }

    static Object importMetadata(Map entity, Map params = null) {
        return updateDsMetadata(entity, params)
    }

    static Object buildHiveTable(Map entity, Map params = null) {
        def db = Database.new
        def ds = db.get(entity)
        def code = EMF.generate([ds], "/pim/dataspace/buildExternalDataset.egl", params)

        def result = runCode(ds, code)

        if (result.status == "OK") {
            updateDsMetadata(entity, params)
        }

        return result
    }

}