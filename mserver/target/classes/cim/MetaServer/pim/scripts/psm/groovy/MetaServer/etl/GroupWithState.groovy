package MetaServer.etl;

import ru.neoflex.meta.utils.Context;
import ru.neoflex.meta.model.Database
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory;
import MetaServer.rt.LivyServer;
import org.eclipse.epsilon.emc.emf.EmfModel;
import org.eclipse.epsilon.common.util.StringProperties;
import MetaServer.utils.EMF;

/* protected region MetaServer.etlGroupWithState.inport on begin */
import MetaServer.utils.Scala
/* protected region MetaServer.etlGroupWithState.inport end */
class GroupWithState {
    /* protected region MetaServer.etlGroupWithState.statics on begin */
    /* protected region MetaServer.etlGroupWithState.statics end */
    private final static Log logger = LogFactory.getLog(Transformation.class);

    public static Object test(Map entity, Map params = null) {
    /* protected region MetaServer.etlGroupWithState.test on begin */
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
    /* protected region MetaServer.etlGroupWithState.test end */
    }
    
	public static Object functionDeclaration(Map entity, Map params = null) {
		def emfModel = EMF.create("src", entity.node)
		
		def code = Context.current.getContextSvc().epsilonSvc.executeEgl("/psm/etl/spark/GroupWithStateFuncDeclaration.egl", [:], [emfModel])
		return [result: true, code: code]
	}
	
    public static Object validateSQLField(Map entity, Map params = null) {
      def code = Context.current.getContextSvc().epsilonSvc.executeEgl("/psm/etl/spark/SqlFieldValidate.egl", [field: entity], [])

      def livyServer = LivyServer.findCurrentLivyServer(Transformation.findOrCreateTRD(entity.parent), params)

      def deployDir = Context.current.getContextSvc().getDeployDir().getAbsolutePath();
      def sessionId = LivyServer.getSessionId(params, livyServer)
      def result = LivyServer.executeStatementAndWait(sessionId, code, logger, livyServer)

      if (result.output.status == 'error') {
          return [result: "text/plain:${result.output}", sessionId:sessionId]
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
