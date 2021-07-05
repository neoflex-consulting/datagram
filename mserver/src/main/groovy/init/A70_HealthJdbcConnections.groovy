import MetaServer.rt.JdbcConnection
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.springframework.boot.actuate.health.AbstractHealthIndicator
import org.springframework.boot.actuate.health.Health
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.Context

import java.sql.DriverManager

Log logger = LogFactory.getLog(this.class)
logger.info("Register Health Indicator for JdbcConnections")
def contextSvc = Context.current.contextSvc
contextSvc.applicationContext.beanFactory.registerSingleton("JdbcConnectionHealthIndicator", new AbstractHealthIndicator() {
    void doHealthCheck(Health.Builder builder) {
        contextSvc.inContext(new Runnable() {
            @Override
            void run() {
                def details = []
                builder.up().withDetail("showStatus", true).withDetail("services", details)
                for (jdbcConnection in Database.new.list("rt.JdbcConnection")) {
                    logger.info("test jdbc connection health for ${jdbcConnection.name}")
                    try {
                        def driver = Context.current.contextSvc.classLoaderSvc.classLoader.loadClass(jdbcConnection.driver).newInstance()
                        DriverManager.registerDriver(driver)
                        def connectionProperties = new Properties()
                        connectionProperties.put("user", jdbcConnection.user)
                        connectionProperties.put("password", JdbcConnection.getPassword(jdbcConnection))
                        def conn = driver.connect(jdbcConnection.url, connectionProperties)
                        details.add([status: "UP", name: jdbcConnection.name, detail: "${conn.metaData.databaseProductName} (${conn.metaData.databaseProductVersion})".toString()])
//                        builder.withDetail(jdbcConnection.name, "${conn.metaData.databaseProductName} (${conn.metaData.databaseProductVersion})".toString())
                        conn.close()
                    }
                    catch (Throwable th) {
                        details.add([status: "DOWN", name: jdbcConnection.name, detail: th.toString()])
                        builder.down()//.withDetail(jdbcConnection.name, th.toString())
                    }
                }
            }
        })
    }
})
