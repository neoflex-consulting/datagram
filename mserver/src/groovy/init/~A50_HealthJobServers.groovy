import groovyx.net.http.RESTClient
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.springframework.boot.actuate.health.AbstractHealthIndicator
import org.springframework.boot.actuate.health.Health
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.Context

Log logger = LogFactory.getLog(this.class)
logger.info("Register Health Indicator for JobServers")
def contextSvc = Context.current.contextSvc
contextSvc.applicationContext.beanFactory.registerSingleton("JobServerHealthIndicator", new AbstractHealthIndicator() {
    void doHealthCheck(Health.Builder builder) {
        contextSvc.inContext(new Runnable() {
            @Override
            void run() {
                builder.up()
                for (jobServer in Database.new.list("rt.JobServer")) {
                    logger.info("test job-server health for ${jobServer.name}")
                    try {
                        def client = new RESTClient(jobServer.http)
                        def resp = client.get(path: "/contexts")
                        builder.withDetail(jobServer.name, resp.statusLine.toString())
                        if (resp.status < 200 || resp.status > 300) {
                            builder.down()
                        }
                    }
                    catch (Throwable th) {
                        builder.down().withDetail(jobServer.name, th.toString())
                    }
                }
            }
        })
    }
})
