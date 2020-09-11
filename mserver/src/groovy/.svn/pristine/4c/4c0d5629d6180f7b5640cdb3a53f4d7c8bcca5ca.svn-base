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
            def srcDir = new File(TransformationDeployment.getSrcDir(trd))
            if (srcDir.isDirectory()) {
                def dstDir = new File(dir, entity.name)
                FileUtils.copyDirectory(srcDir, dstDir, new FileFilter() {
                    @Override
                    boolean accept(File pathname) {
                        String relative = srcDir.toPath().relativize(pathname.toPath()).toString()
                        Boolean accept = (pathname.isDirectory() && relative.startsWith("src")) ||
                                pathname.name.endsWith(".scala") ||
                                pathname.name.endsWith(".xml") ||
                                pathname.name.endsWith(".json")
                        if (accept) {
                            result.add(new File(dstDir, relative))
                        }
                        return accept
                    }
                })
            }
        }
        catch (Throwable th) {
            logger.error("Source code generation failed: ", th)
        }
        return result
    }
}