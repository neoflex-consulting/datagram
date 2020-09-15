import ru.neoflex.meta.utils.Context
import com.fasterxml.jackson.databind.ObjectMapper
import ru.neoflex.meta.utils.JSONHelper
import ru.neoflex.meta.utils.TraversalStrategy

import static ru.neoflex.meta.utils.EMFResource.getTeneoResource

/**
 * Created by orlov on 07.05.2015.
 */
def pp(obj) {
    return (new ObjectMapper()).writer().withDefaultPrettyPrinter().writeValueAsString(obj)
}

res = getTeneoResource(Context.current.contextSvc.teneoSvc.hbds, "query1=from ETL.Mapping")
// res = Context.current.contextSvc.teneoSvc.teneoResource
res.load(null)
def content = res.getContents().collect {it}
return pp(JSONHelper.toJSON("teneo", "", content, TraversalStrategy.DEFAULT))