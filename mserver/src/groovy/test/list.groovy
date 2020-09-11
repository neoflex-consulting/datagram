package test

import com.fasterxml.jackson.databind.ObjectMapper
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.Context
import ru.neoflex.meta.utils.JSONHelper

import static ru.neoflex.meta.utils.EMFResource.loadDirContentToResource

def pp(obj) {
    return (new ObjectMapper()).writer().withDefaultPrettyPrinter().writeValueAsString(obj)
}

def db = new Database("teneo")
db.list("ETL.Mapping").each { db.delete("ETL.Mapping", it)}
db.list("ETL.Database").each { db.delete("ETL.Database", it)}

loadDirContentToResource(new File(Context.current.contextSvc.configDir, "emf/models"), Context.current.contextSvc.teneoSvc.teneoResource);

return pp(db.list("ETL.Mapping").collect { JSONHelper.toJSON("teneo", "ETL.Mapping", it) })