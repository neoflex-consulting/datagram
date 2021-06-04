package MetaServer.etl

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory;
import ru.neoflex.meta.utils.Context;
/* protected region MetaServer.etlCSVTarget.inport on begin */
import MetaServer.rt.TransformationDeployment;
import ru.neoflex.meta.model.Database;
import ru.neoflex.meta.utils.JSONHelper
/* protected region MetaServer.etlCSVTarget.inport end */
class DeltaTarget {
    private final static Log logger = LogFactory.getLog(CSVTarget.class);
    /* protected region MetaServer.etlCSVTarget.statics on begin */
    /* protected region MetaServer.etlCSVTarget.statics end */

    public static Object showContent(Map entity, Map params = null) {
    /* protected region MetaServer.etlLocalTarget.showContent on begin */
        def tr = Database.new.get(entity.transformation)
        def trd = Transformation.findOrCreateTRD(tr)
        Context.current.commit()
        def livyServer
        if (trd.livyServer != null) livyServer = trd.livyServer
        else throw new RuntimeException("Livy Server not found")

        def workflowId = "${trd.transformation.name}_${System.identityHashCode([])}"
        def jobParams = [
                "HOME=${livyServer.home}".toString(),
                "USER=${livyServer.user}".toString(),
                "WF_HOME=${trd.livyServer.home}/${livyServer.user}".toString(),
                "ROOT_WORKFLOW_ID=${workflowId}".toString(),
                "CURRENT_WORKFLOW_ID=${workflowId}".toString(),
                "SLIDE_SIZE=${trd.slideSize}".toString(),
                "FETCH_SIZE=${trd.fetchSize}".toString(),
                "PARTITION_NUM=${trd.partitionNum}".toString(),
                "FAIL_THRESHOLD=${trd.rejectSize}".toString(),
                "DEBUG=${trd.debug}".toString(),
                "MASTER=${trd.master}".toString()
        ]

        trd.parameters.each {
            jobParams.add(JSONHelper.escape("${it.name}=${it.value}").toString())
        }
        if (params != null) {
            for (key in params.keySet()) {
                jobParams.add(JSONHelper.escape("${key}=${params[key]}").toString())
            }
        }
        def fs
        if (entity.target.hdfs == null || entity.target.hdfs == true) fs = '${_defaultFS}' else fs = "file:///"
        def path = 's"""' + fs + entity.target.path + '"""'
        def code = Context.current.getContextSvc().epsilonSvc.executeEgl("/psm/etl/spark/showContent.egl",
                [path: path, format: "delta", size: "${entity.target.sampleSize?:20}", options: entity.target.options ?: [], jobParams: jobParams], [])
        return TransformationDeployment.runPart(trd, [code: code, outputType: 'json', sessionId: entity.sessionId])
    /* protected region MetaServer.etlLocalTarget.showContent end */
    }
}
