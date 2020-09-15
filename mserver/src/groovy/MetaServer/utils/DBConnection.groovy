package MetaServer.utils

import java.sql.Connection
import java.sql.DriverManager

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

class DBConnection {
    
    private final static Log logger = LogFactory.getLog(DBConnection.class);
    
    static Connection getConnection(String url, String user, String password) {
        try {
                return DriverManager.getConnection(url, user, password);
            } catch(Exception e) {
                logger.error(e.getMessage(), e);
                throw e;
            }
      }
}
