package MetaServer.etl;
import ru.neoflex.meta.utils.Context;
/* protected region MetaServer.etlExpressionSource.inport on begin */
import MetaServer.utils.Scala;
/* protected region MetaServer.etlExpressionSource.inport end */
class ExpressionSource {
    /* protected region MetaServer.etlExpressionSource.statics on begin */
    /* protected region MetaServer.etlExpressionSource.statics end */

    public static Object test(Map entity, Map params = null) {
    /* protected region MetaServer.etlExpressionSource.test on begin */
        def scalaSvc = Context.current.contextSvc.scalaSvc
        def result = [:]
        def inputs = [
                [name: "jobParameters", type: "Map[String, AnyRef]"],
        ]
        def script = Scala.makeScript(inputs, entity.expression, "Seq[Predef.Map[String, AnyRef]]")
        scalaSvc.compile(script, inputs, result)
        return result
    /* protected region MetaServer.etlExpressionSource.test end */
    }
}
