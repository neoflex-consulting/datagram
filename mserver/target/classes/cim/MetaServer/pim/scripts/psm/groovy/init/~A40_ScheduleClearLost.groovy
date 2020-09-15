import MetaServer.utils.MetaInfo
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import ru.neoflex.meta.utils.Context

Log logger = LogFactory.getLog(this.class)
def contextSvc = Context.current.contextSvc
new Thread("ClearLostSch") {
    @Override
    void run() {
        def doIt = true
        while (doIt) {
            sleep(6*60*60*1000, {doIt = false})
            if (doIt) {
                logger.info("Run scheduled clear lost objects")
                contextSvc.inContext(new Runnable() {
                    @Override
                    void run() {
                        MetaInfo.getLost([], [])
                    }
                })
            }
        }
    }
}.start()