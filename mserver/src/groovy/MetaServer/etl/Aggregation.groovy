package MetaServer.etl
import ru.neoflex.meta.utils.Context
/* protected region MetaServer.etlAggregation.inport on begin */
import MetaServer.utils.Scala
/* protected region MetaServer.etlAggregation.inport end */
class Aggregation {
    /* protected region MetaServer.etlAggregation.statics on begin */
    /* protected region MetaServer.etlAggregation.statics end */

    static Object testExpression(Map entity, Map params = null) {
    /* protected region MetaServer.etlAggregation.testExpression on begin */
        def scalaSvc = Context.current.contextSvc.scalaSvc
        def result = [:]
        def inputs = [
                [name: "jobParameters", type: "Map[String, AnyRef]"],
                [name: "infield", type: "String"],
                [name: "outfield", type: "String"],
                [name: "accum", type: "java.util.HashMap[String,AnyRef]"],
                [name: "row", type: "java.util.HashMap[String,AnyRef]"],
        ]
        for (field in entity.inputPort.fields) {
            inputs += [name: field.name, type: Scala.getJavaType(field.dataTypeDomain.toString())]
        }
        def script = Scala.makeScript(inputs, entity.expression, "Unit")
        scalaSvc.compile(script, inputs, result)
        return result
    /* protected region MetaServer.etlAggregation.testExpression end */
    }

    static Object testInitExpression(Map entity, Map params = null) {
    /* protected region MetaServer.etlAggregation.testInitExpression on begin */
        def scalaSvc = Context.current.contextSvc.scalaSvc
        def result = [:]
        def inputs = [
                [name: "jobParameters", type: "Map[String, String]"],
                [name: "accum", type: "java.util.HashMap[String,AnyRef]"],
        ]
        def script = Scala.makeScript(inputs, entity.initExpression, "Unit")
        scalaSvc.compile(script, inputs, result)
        return result
    /* protected region MetaServer.etlAggregation.testInitExpression end */
    }

    static Object testFinalExpression(Map entity, Map params = null) {
    /* protected region MetaServer.etlAggregation.testFinalExpression on begin */
        def scalaSvc = Context.current.contextSvc.scalaSvc
        def result = [:]
        def inputs = [
                [name: "jobParameters", type: "Map[String, String]"],
                [name: "accum", type: "java.util.HashMap[String,AnyRef]"],
        ]
        def script = Scala.makeScript(inputs, entity.finalExpression, "Unit")
        scalaSvc.compile(script, inputs, result)
        return result
    /* protected region MetaServer.etlAggregation.testFinalExpression end */
    }

    static Object testMergeExpression(Map entity, Map params = null) {
    /* protected region MetaServer.etlAggregation.testMergeExpression on begin */
        def scalaSvc = Context.current.contextSvc.scalaSvc
        def result = [:]
        def inputs = [
                [name: "jobParameters", type: "Map[String, AnyRef]"],
                [name: "accum1", type: "java.util.HashMap[String,AnyRef]"],
                [name: "accum2", type: "java.util.HashMap[String,AnyRef]"],
        ]
        for (field in entity.inputPort.fields) {
            inputs += [name: field.name, type: Scala.getJavaType(field.dataTypeDomain.toString())]
        }
        def script = Scala.makeScript(inputs, entity.mergeExpression, "Unit")
        scalaSvc.compile(script, inputs, result)
        return result
    /* protected region MetaServer.etlAggregation.testMergeExpression end */
    }
}
