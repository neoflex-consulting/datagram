package MetaServer.sse

import org.eclipse.emf.ecore.EObject
import MetaServer.utils.ECoreHelper

import ru.neoflex.meta.utils.Context

import java.util.Map

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.ECoreUtils

class ModelNotebook {
    private final static Log log = LogFactory.getLog(ModelNotebook.class)

    public static Map deploy(Map entity, Map params = null) {
        //Welcome, wanderer! It is supposed to be rewritten.
        Thread.sleep(4000);
        return [status: "The model has been deployed.", problems: []]
    }
}
