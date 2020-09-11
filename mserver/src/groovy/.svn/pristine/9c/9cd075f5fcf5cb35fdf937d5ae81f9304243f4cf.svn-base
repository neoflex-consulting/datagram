package MetaServer.etl;
import ru.neoflex.meta.utils.Context;
/* protected region MetaServer.etlDebugOutput.inport on begin */
import MetaServer.utils.Scala;
import ru.neoflex.meta.model.Database
/* protected region MetaServer.etlDebugOutput.inport end */
class DebugOutput {
    /* protected region MetaServer.etlDebugOutput.statics on begin */
    /* protected region MetaServer.etlDebugOutput.statics end */

    public static Object test(Map entity, Map params = null) {
    /* protected region MetaServer.etlDebugOutput.test on begin */
        def scalaSvc = Context.current.contextSvc.scalaSvc
        def result = ["result":true]
        if (entity.active && entity.condition != null && !entity.condition.trim().equals("")) {
            def inputs = [
                    [name: "jobParameters", type: "Map[String, String]"],
            ]
            def outputPort = null
            if (entity.outputPort.containsKey("fields")) {
                outputPort = entity.outputPort
            } else {
                outputPort = new Database("teneo").get("etl.OutputPort", (Long)entity.outputPort.e_id)
            }
            for (field in outputPort.fields) {
                inputs += [name: field.name, type: Scala.getJavaType(field.dataTypeDomain.toString())]
            }
            def script = Scala.makeScript(inputs, entity.condition, "java.lang.Boolean")
            scalaSvc.compile(script, inputs, result)
        }
        return result
    /* protected region MetaServer.etlDebugOutput.test end */
    }
}
