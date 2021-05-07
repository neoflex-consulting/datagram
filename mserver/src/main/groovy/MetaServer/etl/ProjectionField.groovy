package MetaServer.etl;

import ru.neoflex.meta.utils.Context;
import ru.neoflex.meta.model.Database
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory;
import MetaServer.rt.LivyServer;
import org.eclipse.epsilon.emc.emf.EmfModel;
import org.eclipse.epsilon.common.util.StringProperties;

/* protected region MetaServer.etlProjectionField.inport on begin */
import MetaServer.utils.Scala

/* protected region MetaServer.etlProjectionField.inport end */

class ProjectionField {
    /* protected region MetaServer.etlProjectionField.statics on begin */
    /* protected region MetaServer.etlProjectionField.statics end */
    private final static Log logger = LogFactory.getLog(Transformation.class);

    public static Object test(Map entity, Map params = null) {
        /* protected region MetaServer.etlProjectionField.test on begin */
        def scalaSvc = Context.current.contextSvc.scalaSvc
        def result = [result: true]
        def inputs = [
                [name: "jobParameters", type: "Map[String, AnyRef]"],
        ]
        for (sourceField in entity.sourceFields) {
            inputs += [name: sourceField.name, type: Scala.getJavaType(sourceField.dataTypeDomain.toString())]
        }
        def script = Scala.makeScript(inputs, entity.expression, Scala.getJavaType(entity.dataTypeDomain.toString()))
        scalaSvc.compile(script, inputs, result)
        return result
        /* protected region MetaServer.etlProjectionField.test end */
    }

    public static Object validateSQLField(Map entity, Map params = null) {
        def trd = Transformation.findOrCreateTRD(entity.parent)
        def code = Context.current.getContextSvc().epsilonSvc.executeEgl("/psm/etl/spark/SqlFieldValidate.egl", [field: entity, parameters: trd.parameters], [])

        def livyServer = LivyServer.findCurrentLivyServer(trd, params)

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
