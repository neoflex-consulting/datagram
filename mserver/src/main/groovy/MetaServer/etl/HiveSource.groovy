package MetaServer.etl

import MetaServer.rt.LivyServer
import MetaServer.utils.EMF
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import ru.neoflex.meta.model.Database;
import ru.neoflex.meta.utils.Context;

import MetaServer.utils.JDBC

class HiveSource {
	private final static Log logger = LogFactory.getLog(Transformation.class);

	public static Object execute(Map entity, Map params = null) {
		Integer sampleSize = 0;
		if (entity.sampleSize != null) {
			sampleSize = entity.sampleSize
		}
		return JDBC.execute(Context.current, entity.context.name, entity.statement, sampleSize)
	}

	public static Object generateAndRunPart(Map entity, Map params = null) {
		def transformation_id = params["transformation_id"] as Long
		def transformation = Database.new.get("etl.Transformation", transformation_id)
		def trd = Transformation.findOrCreateTRD(transformation)
		Context.current.commit()

		def code = Context.current.getContextSvc().epsilonSvc.executeEgl("/psm/etl/spark/SparkSQL.egl", [step: entity, parameters: trd.parameters], [EMF.create("src", transformation)])
		def livyServer = LivyServer.findCurrentLivyServer(trd, params)

		def sessionId = LivyServer.getSessionId(params, livyServer)
		def result = LivyServer.executeStatementAndWait(sessionId, code, logger, livyServer)
		return LivyServer.parseResult(result, "json", sessionId)
	}
}
