import MetaServer.utils.REST
import groovyx.net.http.RESTClient
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.springframework.boot.actuate.health.AbstractHealthIndicator
import org.springframework.boot.actuate.health.Health
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.Context

Log logger = LogFactory.getLog(this.class)
logger.info("Register Health Indicator for Oozie servers")
def contextSvc = Context.current.contextSvc
contextSvc.applicationContext.beanFactory.registerSingleton("OozieHealthIndicator", new AbstractHealthIndicator() {
    void doHealthCheck(Health.Builder builder) {
        contextSvc.inContext(new Runnable() {
            @Override
            void run() {
                def details = []
                builder.up().withDetail("showStatus", true).withDetail("services", details)
                for (oozie in Database.new.list("rt.Oozie")) {
                    logger.info("test oozie health for ${oozie.name}")
                    try {
                        def client = REST.getSimpleHTTPClient( oozie )
                        def resp = client.get(path : "/oozie/versions")
                        details.add([status: "UP", name: oozie.name, detail: resp.statusLine.toString()])
//                        builder.withDetail(oozie.name, resp.statusLine.toString())
                        if (resp.status < 200 || resp.status > 300) {
                            builder.down()
                        }
                    }
                    catch (Throwable th) {
                        details.add([status: "DOWN", name: oozie.name, detail: th.toString()])
                        builder.down()//.withDetail(oozie.name, th.toString())
                    }
                }
            }
        })
    }
})
