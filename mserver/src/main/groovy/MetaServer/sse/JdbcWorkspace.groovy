package MetaServer.sse

import MetaServer.utils.JDBC
import com.google.common.base.Strings
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.Context

import java.sql.DatabaseMetaData
import java.sql.ResultSet

class JdbcWorkspace extends Workspace {
    private final static Log log = LogFactory.getLog(JdbcWorkspace.class)

    private static Map importTable(Map ws, String tabSchema, String tabName, String tabDescription) {
        def db = Database.new
        def fullTableName = ws.name + "_" + tabName
        log.info("Save dataset: ${fullTableName}")
        def tab = db.instantiate("sse.TableDataset",
                [name       : fullTableName,
                 shortName  : tabName,
                 description: tabDescription,
                 workspace  : ws,
                 connection : ws.connection,
                 schema     : tabSchema,
                 tableName  : tabName
                ])
        db.saveOrUpdate(tab)
        log.info("Fetch table structure")
        TableDataset.updateDsMetadata(tab, [:])
        return [status: "OK", table: "${tabSchema}.${tabName}"]
    }

    public static Map importSchema(Map entity, Map params = null) {
        def ws = Database.new.get(entity)
        def cn = ws.connection
        String schema = params.schema ?: ws.defaultSchema
        if (schema == "") {
            schema = null
        }

        if (cn == null) {
            log.error("Empty connection")
            return [status: "ERROR", problems: [entity: ws]]
        }

        def connection
        def imports = []
        try {
            connection = JDBC.connect(Context.current, cn)
            DatabaseMetaData meta = connection.getMetaData()
            if (schema != null && meta.storesUpperCaseIdentifiers()) {
                schema = schema.toUpperCase()
            }
            if (schema != null && meta.storesLowerCaseIdentifiers()) {
                schema = schema.toLowerCase()
            }
            // могут быть проблемы если схема задана в lowercase, а
            ResultSet tables = meta.getTables(null, schema, null, ["TABLE", "VIEW", "MATERIALIZED VIEW"].toArray() as String[])

            while (tables.next()) {
                def tabSchema = tables.getString("TABLE_SCHEM")
                def tabName = tables.getString("TABLE_NAME")
                def tabDescription = tables.getString("REMARKS")
                imports.add(importTable(ws, tabSchema, tabName, tabDescription))
            }
        } catch (Throwable e) {
            log.error("Error while load tables information", e)
            return [status: "ERROR", problems: [e]]
        } finally {
            if (connection != null) {
                connection.close()
            }
        }

        return [status: "OK", problems: [], imports: imports]
    }
}
