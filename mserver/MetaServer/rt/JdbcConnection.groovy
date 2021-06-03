package MetaServer.rt

import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder;
import ru.neoflex.meta.utils.Context;
/* protected region MetaServer.rtJdbcConnection.inport on begin */
import ru.neoflex.meta.utils.Common;
import ru.neoflex.meta.model.Database

import java.sql.Connection
import java.sql.Driver;
import java.sql.DriverManager;
/* protected region MetaServer.rtJdbcConnection.inport end */
class JdbcConnection {
    /* protected region MetaServer.rtJdbcConnection.statics on begin */
    public static String getPassword(Map entity) {
        return Common.getDecryptedPassword("rt.JdbcConnection.${entity.name}.password", entity.password as String)
    }
    /* protected region MetaServer.rtJdbcConnection.statics end */

    public static Object test(Map entity, Map params = null) {
    /* protected region MetaServer.rtJdbcConnection.test on begin */
        def dbname = "teneo"
        def db = new Database(dbname)
        def jdbcConnection = db.get("rt.JdbcConnection", (Long)entity.e_id)
        def conn = getConnection(jdbcConnection)
        conn.close();
        return ["Connected!"]
    /* protected region MetaServer.rtJdbcConnection.test end */
    }

    public static Connection getConnection(Map jdbcConnection) {
        Driver driver = (Driver) Context.current.contextSvc.classLoaderSvc.classLoader.loadClass(jdbcConnection.driver).newInstance()
        DriverManager.registerDriver(driver)

        def connectionProperties = jdbcConnection.connectAsLoggedUser ? fillUserCredentials() :
                fillConnectionCredentials(jdbcConnection)

        return driver.connect(jdbcConnection.url, connectionProperties)
    }

    private static Properties fillConnectionCredentials(Map jdbcConnection) {
        return fillCredentials(jdbcConnection.user, getPassword(jdbcConnection))
    }

    private static Properties fillUserCredentials() {
        Context.User user = Context.current.user
        return fillCredentials(user.getName(), user.getPassword())
    }

    private static Properties fillCredentials(String user, String password) {
        def props = new Properties()

        if (user != null) {
            props.put("user", user)
            if (password != null) {
                props.put("password", password)
            }
        }

        return props
    }
}
