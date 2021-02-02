package MetaServer.utils.extensions

import MetaServer.etl.Transformation
import MetaServer.rt.TransformationDeployment
import org.apache.commons.io.FileUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

class EtlTransformationExt implements Extension {
    private final static Log logger = LogFactory.getLog(EtlTransformationExt.class)

    List export(File dir) {
        def result = []
        try {
            def trd = Transformation.findOrCreateTRD(entity)
            TransformationDeployment.install(trd, [noBuild: true])
        }
        catch (Throwable th) {
            logger.error("Source code generation failed: ", th)
        }
        return result
    }
}