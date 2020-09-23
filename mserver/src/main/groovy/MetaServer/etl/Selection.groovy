package MetaServer.etl

import MetaServer.utils.EMF;
import ru.neoflex.meta.utils.Context;

/* protected region MetaServer.etlSelection.inport on begin */
import MetaServer.utils.Scala
import ru.neoflex.meta.model.Database
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory;
import MetaServer.rt.LivyServer;
import org.eclipse.epsilon.emc.emf.EmfModel;
import org.eclipse.epsilon.common.util.StringProperties;

/* protected region MetaServer.etlSelection.inport end */

class Selection {
    /* protected region MetaServer.etlSelection.statics on begin */
    /* protected region MetaServer.etlSelection.statics end */

    private final static Log logger = LogFactory.getLog(Transformation.class);

    public static Object test(Map entity, Map params = null) {
        def code = Context.current.getContextSvc().epsilonSvc.executeEgl("/psm/etl/spark/SelectionValidate.egl", [step: entity], [])
        def transformation = entity.parent ? Database.new.get(entity.parent) : entity.transformation
        def livyServer = LivyServer.findCurrentLivyServer(Transformation.findOrCreateTRD(transformation), params)

        def deployDir = Context.current.getContextSvc().getDeployDir().getAbsolutePath();
        def sessionId = LivyServer.getSessionId(params, livyServer)
        def result = LivyServer.executeStatementAndWait(sessionId, code, logger, livyServer)

        if (result.output.status == 'error') {
            return [result: "text/plain:${result.output}", sessionId: sessionId]
        } else {
            result = result.output.data
            if (params.outputType == 'json') {
                def jsonData = (result =~ /\{.*\}/)[0]
                return [result: true, message: jsonData, sessionId: sessionId]
            } else {
                return [result: true, message: result, sessionId: sessionId]
            }
        }
    }

}
