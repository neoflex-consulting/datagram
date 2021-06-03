package MetaServer.rt

import ru.neoflex.meta.utils.Context
import ru.neoflex.meta.utils.ECoreUtils
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.JSONHelper
import MetaServer.utils.JDBC
import MetaServer.utils.EMF
import MetaServer.utils.MObject

import java.sql.Connection
import java.sql.DriverManager
import java.util.Map

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

/* protected region MetaServer.rtDeployment.inport end */
class Deployment {
    /* protected region MetaServer.rtDeployment.statics on begin */
    private final static Log logger = LogFactory.getLog(Deployment.class)

    private static void readTables(Connection conn, jdbcConnection, Database db, Map<String, Object> schemeMap, fkList) {
        def rsTables = conn.getMetaData().getTables(jdbcConnection.catalog, jdbcConnection.schema, null, null)
        try {
            def tables = []
            def linkBetweenTablesAndForeing = [:]
            def linkBetweenColsAndForeing = [:]
            while (rsTables.next()) {
                String tableName = rsTables.getString("TABLE_NAME")
                String tableType = rsTables.getString("TABLE_TYPE")
                String tableDescription = rsTables.getString("REMARKS")
                logger.info(tableName + ": " + tableType)
                def table = null
                if (tableType == "TABLE") {
                    table = db.instantiate("rel.Table", [name: tableName, description: tableDescription])
                    schemeMap.tables.add(table)
                } else if (tableType == "VIEW") {
                    table = db.instantiate("rel.View", [name: tableName, description: tableDescription])
                    schemeMap.views.add(table)
                } else {
                    continue
                }
                def rsColumns = conn.getMetaData().getColumns(jdbcConnection.catalog, jdbcConnection.schema, tableName, null)
                try {
                    while (rsColumns.next()) {
                        String columnName = rsColumns.getString("COLUMN_NAME")
                        String description = rsColumns.getString("REMARKS")                        
                        boolean nullable = rsColumns.getInt("NULLABLE") == 1
                        int dataType = rsColumns.getInt("DATA_TYPE")
                        int decimalDigits = rsColumns.getInt("DECIMAL_DIGITS")
                        int charOctetLength = rsColumns.getInt("CHAR_OCTET_LENGTH")
                        String typeName = rsColumns.getString("TYPE_NAME")
                        def colDataType = JDBC.getDataType(dataType, typeName, decimalDigits, charOctetLength)
                        if (colDataType != null) {
                            def column = db.instantiate("rel.Column", [name: columnName, nullable: nullable, dataType: colDataType, description: description]) 
                            table.columns.add(column)
                        }
                    }
                }
                finally {
                    rsColumns.close()
                }
                if (tableType == "TABLE") {
                    def pkList = []
                    def rsPK = conn.metaData.getPrimaryKeys(jdbcConnection.catalog, jdbcConnection.schema, tableName)
                    try {
                        while (rsPK.next()) {
                            String columnName = rsPK.getString("COLUMN_NAME")
                            pkList.add(columnName)
                        }
                    }
                    finally {
                        rsPK.close()
                    }
                    //db.save(table)
                    if (pkList.size() > 0) {
                        def pk = db.instantiate("rel.PrimaryKey", [name: tableName + "_pk"])
                        table.primaryKey = pk
                        pk.keyFeatures.addAll(pkList.collect {
                            def columnName = it
                            def feature = db.instantiate("rel.KeyFeature")
                            feature.column = table.columns.find { it.name == columnName }
                            feature
                        })
                    }
                    
//                    def fkList = []
                    def rsFK = conn.metaData.getImportedKeys(jdbcConnection.catalog, jdbcConnection.schema, tableName)
                    try {
                        while (rsFK.next()) {
                            fkList.add([
                                "foreingTable": rsFK.getString("PKTABLE_NAME"),
                                "foreingTablePkColumn": rsFK.getString("PKCOLUMN_NAME"),
                                "fkColumn": rsFK.getString("FKCOLUMN_NAME"),
                                "tableName": tableName,
                                "name": rsFK.getString("FK_NAME")
                                ] )
                        }
                    }
                    finally {
                        rsFK.close()
                    }
                    //db.save(table)
                }
                //db.save(table)
                tables.add(table)
            }
        }
        finally {
            rsTables.close()
        }
    }

    private
    static void readProcedures(Connection conn, jdbcConnection, Database db, String dbname, Map<String, Object> schemeMap) {
        if (schemeMap.loadStoredProcs != true) {
            return
        }
        def rsProcedures = conn.getMetaData().getProcedures(jdbcConnection.catalog, jdbcConnection.schema, null)
        try {
            while (rsProcedures.next()) {
                String procedureName = rsProcedures.getString("PROCEDURE_NAME")
                String description = rsProcedures.getString("REMARKS")
                String procedureCat = rsProcedures.getString("PROCEDURE_CAT")
                String procedureSchem = rsProcedures.getString("PROCEDURE_SCHEM")
                int procedureType = rsProcedures.getShort("PROCEDURE_TYPE")
                String specificName = rsProcedures.getString("SPECIFIC_NAME")
                String fullName = procedureName
                if (procedureCat != null) {
                    fullName = procedureCat + "." + fullName
                }
                if (procedureSchem != null) {
                    fullName = procedureSchem + "." + fullName
                }

                logger.info(fullName + '(' + specificName + ')' + ": " + procedureType)
                def procedure = db.instantiate("rel.StoredProcedure", [name: procedureName, catalogName: procedureCat, description: description])
                if (procedureType == 1) procedure.spType = JSONHelper.getEnumerator(dbname, "rel.StoredProcedure", "spType", "NORESULT")
                else if (procedureType == 2) procedure.spType = JSONHelper.getEnumerator(dbname, "rel.StoredProcedure", "spType", "RETURNSRESULT")
                else procedure.spType = JSONHelper.getEnumerator(dbname, "rel.StoredProcedure", "spType", "UNKNOWN")
                schemeMap.storedProcedures.add(procedure)
                def rsParameters = conn.getMetaData().getProcedureColumns(null, jdbcConnection.schema, procedureName, null)
                try {
                    while (rsParameters.next()) {
                        String columnName = rsParameters.getString("COLUMN_NAME")
                        String coldescription = rsParameters.getString("REMARKS")
                        boolean nullable = rsParameters.getInt("NULLABLE") == 1
                        int columnType = rsParameters.getInt("COLUMN_TYPE")
                        int dataType = rsParameters.getInt("DATA_TYPE")
                        int precision = rsParameters.getInt("PRECISION")
                        int length = rsParameters.getInt("LENGTH")
                        int scale = rsParameters.getShort("SCALE")
                        String typeName = rsParameters.getString("TYPE_NAME")
                        if (columnName == null) {
                            columnName = '<null>'
                        }
                        def column = db.instantiate("rel.SPColumn", [name: columnName, nullable: nullable, description: coldescription])
                        if (columnType == 1) column.columnType = JSONHelper.getEnumerator(dbname, "rel.SPColumn", "columnType", "IN")
                        else if (columnType == 2) column.columnType = JSONHelper.getEnumerator(dbname, "rel.SPColumn", "columnType", "INOUT")
                        else if (columnType == 3) column.columnType = JSONHelper.getEnumerator(dbname, "rel.SPColumn", "columnType", "RESULT")
                        else if (columnType == 4) column.columnType = JSONHelper.getEnumerator(dbname, "rel.SPColumn", "columnType", "OUT")
                        else if (columnType == 5) column.columnType = JSONHelper.getEnumerator(dbname, "rel.SPColumn", "columnType", "RETURN")
                        else column.columnType = column.columnType = JSONHelper.getEnumerator(dbname, "rel.SPColumn", "columnType", "UNKNOWN")
                        column.dataType = JDBC.getDataType(dataType, typeName, precision, length)
                        if (column.dataType == null)
                            continue
                        procedure.columns.add(column)
                    }
                }
                finally {
                    rsParameters.close()
                }
                //db.save(procedure)
            }
        }
        finally {
            rsProcedures.close()
        }
    }
    /* protected region MetaServer.rtDeployment.statics end */

    static Object refreshScheme(Map entity, Map params = null) {
    /* protected region MetaServer.rtDeployment.refreshScheme on begin */
        def dbname = "teneo"
        def db = new Database(dbname)
        def deployment = db.get("rt.Deployment", (Long)entity.e_id)
        def jdbcConnection = deployment.connection
        def softwareSystem = deployment.softwareSystem
        def schemeType = "rel.Scheme"
        def newScheme = db.instantiate(schemeType)
        newScheme.name = (jdbcConnection.catalog?:"") + (jdbcConnection.schema?:"") + "_at_" + jdbcConnection.name
        def driver = Context.current.contextSvc.classLoaderSvc.classLoader.loadClass(jdbcConnection.driver).newInstance()
        DriverManager.registerDriver(driver)
        def connectionProperties = new Properties()
        if (jdbcConnection.user != null) {
            connectionProperties.put("user", jdbcConnection.user)
            def password = JdbcConnection.getPassword(jdbcConnection)
            if (password != null) {
                connectionProperties.put("password", password)
            }
        }
        def conn = driver.connect(jdbcConnection.url, connectionProperties)
        def fkList = []
        try {
            readTables(conn, jdbcConnection, db, newScheme, fkList)
            readProcedures(conn, jdbcConnection, db, dbname, newScheme)
        }
        finally {
            conn.close()
        }

        if (fkList.size() > 0) {
            for(table in newScheme.tables) {
                def foreignKeys = []
                foreignKeys.addAll(fkList.findAll { it.tableName == table.name }.collect {
                    def fk = db.instantiate("rel.ForeignKey", [name: it.name])
                    def feature = db.instantiate("rel.KeyFeature")
                    def columnName = it.fkColumn
                    def foreingTable = it.foreingTable
                    feature.column = table.columns.find { it.name == columnName }
                    fk.keyFeatures = [feature]
                    fk.target = newScheme.tables.find {it.name == foreingTable}
                    fk
                })
                table.foreignKeys = foreignKeys
            }
        }

        //db.save(newScheme)
        ECoreUtils.merge(softwareSystem.scheme, newScheme)
        db.save(newScheme)
        def oldScheme = softwareSystem.scheme
        softwareSystem.scheme = newScheme
        softwareSystem.defaultDeployment = deployment
        db.save(softwareSystem)
        if (oldScheme) {
            db.delete(oldScheme._type_, oldScheme)
        }
        //return JSONHelper.toJSON(dbname, softwareSystem.scheme._type_, softwareSystem.scheme)
        return [softwareSystem.scheme.name]
    /* protected region MetaServer.rtDeployment.refreshScheme end */
    }
}
