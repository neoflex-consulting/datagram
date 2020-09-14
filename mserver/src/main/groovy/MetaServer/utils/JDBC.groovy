package MetaServer.utils

import org.apache.commons.dbcp.BasicDataSource
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.SimpleDriverDataSource
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.Context

import javax.sql.DataSource
import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.sql.Types

/**
 * Created by orlov on 25.12.2015.
 */
class JDBC {
    private final static Log logger = LogFactory.getLog(JDBC.class)

    public static Map getDataType(dataType, typeName, decimalDigits, charOctetLength) {
        def db = new Database("teneo")
        switch (dataType) {
            case Types.BIT:
            case Types.TINYINT:
            case Types.SMALLINT:
            case Types.INTEGER:
                return db.instantiate("rel.INTEGER")
                break
            case Types.BIGINT:
                return db.instantiate("rel.LONG")
                break
            case Types.NUMERIC:
                /* NRCNRDEVI-1805
                if (decimalDigits == 0) {
                    if (charOctetLength == 1) {
                        return db.instantiate("rel.BOOLEAN")
                    }
                    else if (charOctetLength in [3, 5, 10]) {
                        return db.instantiate("rel.INTEGER")
                    }
                    else if (charOctetLength == 19) {
                        return db.instantiate("rel.LONG")
                    }
                }
                else if (decimalDigits == 4 && charOctetLength == 19) {
                    return db.instantiate("rel.FLOAT")
                }
                */
                return db.instantiate("rel.DECIMAL", [length:(charOctetLength == 0 ? 38 : charOctetLength), precision:(decimalDigits == -127 ? 0 : decimalDigits)])
                break;
            case Types.DECIMAL:
                return db.instantiate("rel.DECIMAL", [length:(charOctetLength == 0 ? 38 : charOctetLength), precision:(decimalDigits == -127 ? 0 : decimalDigits)])
                break;
            case Types.FLOAT:
                return db.instantiate("rel.FLOAT")
                break;
            case Types.REAL:
            case Types.DOUBLE:
                return db.instantiate("rel.DOUBLE")
                break;
            case Types.VARCHAR:
            case Types.NVARCHAR:
            case Types.LONGVARCHAR:
            case Types.NCLOB:
            case Types.SQLXML:
            case Types.CLOB:
            case  Types.ROWID:
                return db.instantiate("rel.VARCHAR", [length:(charOctetLength == 0 ? 255 : charOctetLength)])
                break
            case Types.CHAR:
            case Types.NCHAR:
                return db.instantiate("rel.CHAR", [length:charOctetLength])
                break
            case Types.BLOB:
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
                return db.instantiate("rel.BLOB")
                break
            case Types.DATE:
            case Types.TIME:
            case Types.TIMESTAMP:
                def typeNameU = typeName.toUpperCase()
                if (typeNameU.startsWith("TIMESTA")) {
                    return db.instantiate("rel.DATETIME")
                }
                else if (typeNameU.startsWith("TIME")) {
                    return db.instantiate("rel.TIME")
                }
                else if (typeNameU.startsWith("DATE")) {
                    //return db.instantiate("rel.DATE")
                    return db.instantiate("rel.DATETIME")
                }
                else {
                    return null
                }
                break
            case Types.BOOLEAN:
                return db.instantiate("rel.BOOLEAN")
                break
            case Types.OTHER:
                return db.instantiate("rel.DATETIME")
                break
            case Types.ARRAY:
                return db.instantiate("rel.ARRAY")
                break
            case Types.NULL:
            default:
                return null
        }
    }
    public static Object execute(Context current, String contextName, String statement, Integer sampleSize) {
        Map rtDeployment = findDeploymentByContextName(contextName)
        def jdbcConnection = rtDeployment.connection
        return select(current, jdbcConnection, statement, sampleSize)
    }

    public static Map findDeploymentByContextName(String contextName) {
        def dbname = "teneo"
        def db = new Database(dbname)
        def rtSoftwareSystems = db.list("rt.SoftwareSystem", [name: contextName])
        if (rtSoftwareSystems.size() == 0) throw new Exception("SoftwareSystem $contextName not found")
        def rtSoftwareSystem = rtSoftwareSystems.first()
        def rtDeployment = rtSoftwareSystem.defaultDeployment
        if (rtDeployment == null) {
            def rtDeployments = db.list("rt.Deployment", ["softwareSystem.e_id": rtSoftwareSystem.e_id])
            if (rtDeployments.size() != 1) throw new Exception("Deployment for SoftwareSystem '$contextName' not found or not unique")
            rtDeployment = rtDeployments.first()
        }
        rtDeployment
    }

    public static LinkedHashMap<String, List> select(
            Context current,
            jdbcConnection,
            String statement,
            int sampleSize) {
        return selectAfterUpdates(current, jdbcConnection, statement, sampleSize, [])
    }

    public static LinkedHashMap<String, List> selectAfterUpdates(
            Context current,
            jdbcConnection,
            String statement,
            int sampleSize,
            List<String> updates) {
        Connection conn = connect(current, jdbcConnection)
        try {
            updateListOnConnection(updates, conn)
            return selectOnConnection(statement, sampleSize, conn)
        }
        finally {
            conn.close()
        }
    }

    static LinkedHashMap<String, List> selectOnConnection(String statement, int sampleSize, Connection connection) {
        def stmt = connection.prepareStatement(statement)
        try {
            if (sampleSize != -1) {
                stmt.setMaxRows(sampleSize == 0 ? 100 : sampleSize)
            }
            def rs = stmt.executeQuery()
            try {
                def meta = rs.metaData
                def columnCount = meta.columnCount
                def columns = []
                for (int i = 1; i <= columnCount; ++i) {
                    def column = [_type_: "rel.Column", name: meta.getColumnName(i), nullable: meta.isNullable(i)]
                    column.dataType = getDataType(meta.getColumnType(i), meta.getColumnTypeName(i), meta.getScale(i), meta.getPrecision(i))
                    columns.add(column)
                }
                def rows = []
                while (rs.next()) {
                    def row = []
                    for (int i = 1; i <= columnCount; ++i) {
                        def object = rs.getObject(i)
                        row.add(object == null ? null : object.toString())
                    }
                    rows.add(row)
                }
                return [columns: columns, rows: rows]
            }
            finally {
                rs.close()
            }
        }
        finally {
            stmt.close()
        }
    }

    public static int update(Context current, jdbcConnection, String statement) {
        return updateList(current, jdbcConnection, [statement]).first()
    }

    public static List updateList(Context current, jdbcConnection, List<String> statements) {
        Connection conn = connect(current, jdbcConnection)
        try {
            return updateListOnConnection(statements, conn)
        }
        finally {
            conn.close()
        }
    }

    private static ArrayList updateListOnConnection(List<String> statements, Connection conn) {
        def counts = []
        statements.each { statement ->
            def stmt = conn.prepareStatement(statement)
            try {
                counts.add(stmt.executeUpdate())
            }
            finally {
                stmt.close()
            }
        }
        return counts
    }

    public static Connection connect(Context current, jdbcConnection) {
        getDataSource(jdbcConnection).connection
    }

    public static Connection connect(jdbcConnection) {
        getDataSource(jdbcConnection).connection
    }

    public static DataSource getDataSource(jdbcConnection) {
        def dataSource = new SimpleDriverDataSource()
        def driver = Context.current.contextSvc.classLoaderSvc.classLoader.loadClass(jdbcConnection.driver).newInstance() as Driver
        DriverManager.registerDriver(driver)
        dataSource.setDriver(driver)
        dataSource.setUrl(jdbcConnection.url);
        if (jdbcConnection.connectAsLoggedUser) {
            Context.User user = Context.current.user
            if (user.name == null) {
                logger.error("Connection ${jdbcConnection.name} marked as connect as logged user, but no user info provided")
                throw new IllegalArgumentException("Empty user info")
            }
            dataSource.setUsername(user.getName());
            dataSource.setPassword(user.getPassword());
        } else {
            dataSource.setUsername(jdbcConnection.user);
            dataSource.setPassword(jdbcConnection.password);
        }
        return dataSource
    }
}
