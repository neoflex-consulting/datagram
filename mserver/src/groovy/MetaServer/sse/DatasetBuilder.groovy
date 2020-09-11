package MetaServer.sse

import MetaServer.utils.EMF
import com.google.common.base.Strings
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import ru.neoflex.meta.utils.Context

class DatasetBuilder {
    private final static Log log = LogFactory.getLog(DatasetBuilder.class)

    private final List<String> buildOrder = new ArrayList<>()
    boolean isFullRebuild = false
    AbstractDataset root = null
    Map<String, Object> params

    List<String> build(AbstractDataset root, boolean isFullRebuild, Map<String, Object> params) {
        log.info("Generatin code for ${root.dataset.name}, full rebuild: ${isFullRebuild}")
        this.isFullRebuild = isFullRebuild
        this.root = root
        this.params = params
        buildOrder.clear()
        injectParameters(root)
        root.build(this)
        return buildOrder
    }

    void visitBuild(Map ds) {
        log.info("Visit build for ${ds.name}")
        def sources = [ds]
        def params = [:]
        if (ds._type_ == "sse.Dataset" && Strings.isNullOrEmpty(ds.expression)) {
            log.error("Empty expression for dataset: ${ds.name}. Nothing to build")
            return
        }
        buildOrder.add(EMF.generate(sources, "/pim/dataspace/build.egl", params))
    }

    void visitRegister(Map ds, String registerName) {
        log.info("Visit register for ${ds.name}")
        def sources = [ds]
        Context.User user = Context.current.user
        def userMap = [userName: user.name, password: user.password]
        def params = [registerName: registerName, user: userMap]
        buildOrder.add(EMF.generate(sources, "/pim/dataspace/register.egl", params))
    }

    void injectParameters(AbstractDataset ds) {
        log.info("Inject parameters")
        def sources = [ds.dataset.workspace]
        def params = [:]
        buildOrder.add(EMF.generate(sources, "/pim/dataspace/injectParameters.egl", params))
    }

}
