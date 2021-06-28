package init

import MetaServer.utils.REST
import groovyx.net.http.RESTClient
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.springframework.boot.actuate.health.AbstractHealthIndicator
import org.springframework.boot.actuate.health.Health
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.Context

Log logger = LogFactory.getLog(this.class)
logger.info("Register Health Indicator for LivyServer-s")
def contextSvc = Context.current.contextSvc
contextSvc.applicationContext.beanFactory.registerSingleton("LivyServerHealthIndicator", new AbstractHealthIndicator() {
    void doHealthCheck(Health.Builder builder) {
        contextSvc.inContext(new Runnable() {
            @Override
            void run() {
                def details = []
                builder.up().withDetail("showStatus", true).withDetail("services", details)
                for (livyServer in Database.new.list("rt.LivyServer")) {
                    logger.info("test livy-server health for ${livyServer.name}")
                    try {
                        def client = REST.getSimpleHTTPClient(livyServer)
                        def resp = client.get(
                                path : "/sessions",
                                requestContentType : groovyx.net.http.ContentType.JSON,
                                contentType : groovyx.net.http.ContentType.JSON)
                        details.add([status: "UP", name: livyServer.name, detail: resp.statusLine.toString()])
//                        builder.withDetail(livyServer.name, resp.statusLine.toString())
                        if (resp.status < 200 || resp.status > 300) {
                            builder.down()
                        }
                    }
                    catch (Throwable th) {
                        details.add([status: "DOWN", name: livyServer.name, detail: th.toString()])
                        builder.down()//.withDetail(livyServer.name, th.toString())
                    }
                }
            }
        })
    }
})
