package MetaServer.utils

import org.apache.commons.io.FileUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.eclipse.epsilon.common.util.StringProperties
import org.eclipse.epsilon.emc.emf.EmfModel
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.Context
/* protected region MetaServer.rtWorkflowDeployment.inport on begin */
import ru.neoflex.meta.utils.MetaResource


class GenerationBase {

    static Object chain(fs, Map entity, Map params = null) {
        def problems = []
        def data = params ?: [:]
        for (f in fs) {
            def ret = f(entity, data)
            problems += ret.problems
            data.putAll(ret.data ?: [:])
            if (!ret.result) return [result: false, problems: problems, data: data]
        }
        return [result: true, problems: problems, data: data]
    }
}