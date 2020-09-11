package MetaServer.sse

import MetaServer.utils.JDBC
import com.google.common.base.Strings
import oracle.jdbc.driver.OracleConnection
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.Context

import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet

class TableDataset extends JdbcDataset {

    TableDataset() {
        super()
    }

    TableDataset(Map entity) {
        super(entity)
    }

    static Object fetchData(Map entity, Map params = null) {
        Database db = Database.new
        Map ds = db.get(entity)
        Map cn = ds.connection
        String schema = entity.schema ?: ds.schema
        String table = entity.tableName ?: ds.tableName
        Integer batchSize = Integer.valueOf(params?.batchSize ?: "100")

        if (cn == null || Strings.isNullOrEmpty(schema) || Strings.isNullOrEmpty(table)) {
            log.error("Cannot find connection/query on dataset: ${ds.name}")
            return [status: "ERROR", problems: ["Empty connection/query"]]
        }

        def query = "select * from ${schema}.${table}"

        if (!Strings.isNullOrEmpty(params?.filter)) {
            query = "select t.* from (${query}) t where ${params.filter}"
        }

        try {
            log.debug("Executing query: ${query}")
            def result = JDBC.select(Context.current, cn, query, batchSize)

            if (ds.columns == null || ds.columns.size() == 0 || ds.columns.size() != result.columns.size()) {
                updateDsMetadata(ds, [:])
            }

            return [status: "OK", columns: columns2Json(ds), rows: result.rows]

        } catch (Throwable e) {
            log.error("Error while executing query", e)
            return [status: "ERROR", problems: [e.getMessage()]]
        }
    }

    static Map updateDsMetadata(Map entity, Map params = null) {
        Database db = Database.new
        Map ds = entity //db.get(entity)
        Map cn = ds.connection
        String schema = ds.schema
        String table = ds.tableName

        if (cn == null || Strings.isNullOrEmpty(schema) || Strings.isNullOrEmpty(table)) {
            log.error("Cannot find connection/query on dataset: ${ds.name}")
            return [status: "ERROR", problems: ["Empty connection/query"]]
        }

        Connection conn
        try {
            conn = JDBC.connect(Context.current, cn)
            // https://stackoverflow.com/questions/37612183/how-to-get-column-comments-in-jdbc
            if (conn instanceof OracleConnection) {
                (conn as OracleConnection).setRemarksReporting(true)
            }

            DatabaseMetaData meta = conn.getMetaData()
            ResultSet columns = meta.getColumns(null, schema.toUpperCase(), table.toUpperCase(), null)
            ds.columns?.clear()
            while (columns.next()) {
                def colParams = [columnType : "ScalarType",
                                 columnName : columns.getString("COLUMN_NAME"),
                                 description: columns.getString("REMARKS"),
                                 isNullable : columns.getString("IS_NULLABLE") == "YES",
                                 length     : columns.getInt("COLUMN_SIZE"),
                                 precision  : columns.getInt("DECIMAL_DIGITS"),
                                 nativeType : columns.getString("TYPE_NAME").toUpperCase(),
                                 typ        : jdbc2DataType(columns.getInt("DATA_TYPE"))
                ]
                def column = createColumn(db, colParams)
                ds.columns.add(column)
            }
            db.saveOrUpdate(ds)
            db.commit()
        } catch (Throwable e) {
            log.error("Error while fetching metadata", e)
            return [status: "ERROR", problems: [e.getMessage()]]
        } finally {
            if (conn != null) {
                conn.close()
            }
        }

        return [status: "OK", problems: []]
    }

    static Object importMetadata(Map entity, Map params = null) {
        return updateDsMetadata(entity, params)
    }
}