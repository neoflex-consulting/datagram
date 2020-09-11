/* not critical but time consuming
import MetaServer.etl.Project
import MetaServer.rt.JdbcConnection
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.springframework.boot.actuate.health.AbstractHealthIndicator
import org.springframework.boot.actuate.health.Health
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.Context

Log logger = LogFactory.getLog(this.class)
logger.info("Register Health Indicator for SVN")
def contextSvc = Context.current.contextSvc
contextSvc.applicationContext.beanFactory.registerSingleton("SVNHealthIndicator", new AbstractHealthIndicator() {
    void doHealthCheck(Health.Builder builder) {
        contextSvc.inContext(new Runnable() {
            @Override
            void run() {
                builder.up()
                def projects = Database.new.list("etl.Project").findAll{it.svnEnabled == true}
                for (project in projects) {
                    logger.info("test svn health for project ${project.name}")
                    try {
                        def info = contextSvc.svnSvc.getURLInfo(project.svnUserName, Project.getPassword(project), project.svnURL)
                        builder.withDetail(project.name, info.urlString)
                    }
                    catch (Throwable th) {
                        builder.down().withDetail(project.name, th.toString())
                    }
                }
            }
        })
    }
})
*/