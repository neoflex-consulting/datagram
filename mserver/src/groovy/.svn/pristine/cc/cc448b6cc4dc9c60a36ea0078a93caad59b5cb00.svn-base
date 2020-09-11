package MetaServer.sse

import MetaServer.utils.EMF
import MetaServer.utils.JDBC
import com.google.common.base.Strings
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.springframework.batch.item.ExecutionContext
import org.springframework.batch.item.file.FlatFileItemReader
import org.springframework.batch.item.file.mapping.ArrayFieldSetMapper
import org.springframework.batch.item.file.mapping.DefaultLineMapper
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer
import org.springframework.core.io.InputStreamResource
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.Context

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

class ReferenceDataset extends AbstractDataset {
    private final static Log log = LogFactory.getLog(ReferenceDataset.class)

    ReferenceDataset() {
        super()
    }

    ReferenceDataset(Map entity) {
        super(entity)
    }

    @Override
    void build(DatasetBuilder builder) {
        builder.visitRegister(this.dataset, this.dataset.shortName)
    }

    static getTableName(Map entity) {
        return entity.name
    }

    public static Object createTable(Map entity, Map<String, Object> params = null) {
        def ds = Database.new.get(entity)
        try {
            def cn = Workspace.getReferenceConnection(ds.workspace)
            def sqlList = []
            params = [drop: "false", create: "true"] + (params ?: [:])
            if (params.drop == "true") {
                def dropSql = EMF.generate([entity], "/pim/dataspace/dropReferenceTable.egl", params)
                sqlList.add(dropSql)
            }
            if (params.create == "true") {
                def createSql = EMF.generate([entity], "/pim/dataspace/createReferenceTable.egl", params)
                sqlList.add(createSql)
            }
            def counts = JDBC.updateList(Context.current, cn, sqlList)
            return [status: "OK", counts: counts]
        } catch (Throwable e) {
            log.error("Error while creating table for dataset ${ds.name}", e)
            return [status: "ERROR", problems: [e.getMessage()]]
        }
    }

    public static Object recreateTable(Map entity, Map params = null) {
        params = [drop: "true"] + (params ?: [:])
        return createTable(entity, params)
    }

    public static Object dropTable(Map entity, Map params = null) {
        params = [drop: "true", create: "false"] + (params ?: [:])
        return createTable(entity, params)
    }

    public static Object upsert(Map entity, Map params = null) {
        params = params ?: [:]
        try {
            def ds = entity //db.get(entity)
            def values = [:]
            ds.columns.each {values[it.columnName] = Column.fromString(it, params[it.columnName])}
            def cn = Workspace.getReferenceConnection(ds.workspace)
            String table = getTableName(ds)
            def query = "UPSERT INTO ${table}(${ds.columns.collect {it.columnName}.join(',')}) VALUES(${ds.columns.collect {'?'}.join(',')})"
            //return query
            Connection conn = JDBC.connect(Context.current, cn)
            try {
                def stmt = conn.prepareStatement(query)
                try {
                    ds.columns.eachWithIndex { column, index ->
                        Object value = values[column.columnName]
                        stmt.setObject(index + 1, value)
                    }
                    def count = stmt.executeUpdate()
                    conn.commit()
                    return [status: "OK", count: count]
                }
                finally {
                    stmt.close()
                }
            }
            finally {
                conn.close()
            }
        } catch (Throwable e) {
            log.error("Error while inserting dataset ${entity.name}", e)
            return [status: "ERROR", problems: [e.getMessage()]]
        }
    }

    public static Object delete(Map entity, Map params = null) {
        params = params ?: [:]
        try {
            def ds = entity //db.get(entity)
            def keyColumns = ds.columns.findAll {column -> ds.primaryKeyCols.any {it == column.columnName}}
            def values = [:]
            keyColumns.each {values[it.columnName] = Column.fromString(it, params[it.columnName])}
            def cn = Workspace.getReferenceConnection(ds.workspace)
            String table = getTableName(ds)
            def query = "DELETE FROM ${table} WHERE ${keyColumns.collect {it.columnName + ' = ?'}.join(' AND ')}"
            Connection conn = JDBC.connect(Context.current, cn)
            try {
                def stmt = conn.prepareStatement(query)
                try {
                    keyColumns.eachWithIndex { column, index ->
                        Object value = values[column.columnName]
                        stmt.setObject(index + 1, value)
                    }
                    def count = stmt.executeUpdate()
                    conn.commit()
                    return [status: "OK", count: count]
                }
                finally {
                    stmt.close()
                }
            }
            finally {
                conn.close()
            }
        } catch (Throwable e) {
            log.error("Error while inserting dataset ${entity.name}", e)
            return [status: "ERROR", problems: [e.getMessage()]]
        }
    }

    static Object fetchData(Map entity, Map params = null) {
        params = params ?: [:]
        def ds = Database.new.get(entity)
        try {
            Integer batchSize = Integer.valueOf(params?.batchSize ?: "1000")

            def keyColumns = (ds.columns as List).findAll {column -> ds.primaryKeyCols.any {it == column.columnName}}
            def values = [:]
            keyColumns.each {
                if (params.hasProperty(it.columnName)) {
                    values[it.columnName] = Column.fromString(it, params[it.columnName])
                }
            }
            def columns = [:]
            (ds.columns as List).each {columns[it.columnName] = it}
            def cn = Workspace.getReferenceConnection(ds.workspace)
            String table = getTableName(ds)
            def query = "select ${(ds.columns as List).collect {it.columnName}.join(", ")} from ${table}"
            if (!Strings.isNullOrEmpty(params?.filter)) {
                query = query + " where ${params.filter}"
            }
            if (values.size() > 0) {
                query = query + " WHERE ${keyColumns.collect {it.columnName + ' = ?'}.join(' AND ')}"
            }

            if (cn == null || Strings.isNullOrEmpty(table)) {
                log.error("Cannot find connection/query on dataset: ${ds.name}")
                return [status: "ERROR", problems: ["Empty connection/query"]]
            }

            Connection conn = JDBC.connect(Context.current, cn)
            try {
                PreparedStatement ps = conn.prepareStatement(query)
                if (batchSize != -1) {
                    ps.setMaxRows(batchSize == 0 ? 100 : batchSize)
                }
                if (values.size() > 0) {
                    keyColumns.eachWithIndex { column, index ->
                        Object value = values[column.columnName]
                        ps.setObject(index + 1, value)
                    }
                }
                ResultSet rs = ps.executeQuery()
                def rows = []
                def columnCount = rs.metaData.columnCount
                while (rs.next()) {
                    def row = []
                    for (int i = 1; i <= columnCount; ++i) {
                        def object = rs.getObject(i)
                        def columnName = rs.metaData.getColumnName(i)
                        def column = columns[columnName]
                        object = Column.encode(column, object)
                        row.add(object == null ? null : object.toString())
                    }
                    rows.add(row)
                }

                return [status: "OK", columns: columns2Json(ds), rows: rows]
            }
            finally {
                conn.close()
            }
        } catch (Throwable e) {
            log.error("Error while query dataset ${ds.name}", e)
            return [status: "ERROR", problems: [e.getMessage()]]
        }
    }
    static Object loadCSV(Map entity, Map params = null) {
        try {
            params = params ?: [:]
            def columnNames = (entity.columns as List).collect{it.columnName as String}
            def file = params.file
            def inputStream = file.inputStream
            def reader = new FlatFileItemReader<String[]>()
            reader.setResource(new InputStreamResource(inputStream))
            if (!Strings.isNullOrEmpty(params.skip)) {
                reader.setLinesToSkip((params.skip as String).toInteger())
            }
            if (!Strings.isNullOrEmpty(params.encoding)) {
                reader.setEncoding(params.encoding)
            }
            def tokenizer = new DelimitedLineTokenizer(){{
                setNames(columnNames as String[])
                if (!Strings.isNullOrEmpty(params.delimiter)) {
                    setDelimiter(params.delimiter)
                }
                if (!Strings.isNullOrEmpty(params.quote)) {
                    setQuoteCharacter(params.quote)
                }
            }}
            reader.setLineMapper(new DefaultLineMapper<String[]>() {{
                setLineTokenizer(tokenizer)
                setFieldSetMapper(new ArrayFieldSetMapper())
            }})
            reader.open(new ExecutionContext())
            try {
                String table = getTableName(entity)
                def query = "UPSERT INTO ${table}(${columnNames.join(',')}) VALUES(${columnNames.collect {'?'}.join(',')})"
                def cn = Workspace.getReferenceConnection(entity.workspace)
                def dataSource = JDBC.getDataSource(cn)
                def connection = dataSource.getConnection()
                try {
                    def statement = connection.prepareStatement(query)
                    def batchSize = 10
                    try {
                        def count = 0
                        while (true) {
                            def row = reader.read()
                            if (row == null) {
                                if (count > 0) {
                                    connection.commit()
                                }
                                break
                            }
                            if (count > 0 && count%batchSize == 0) {
                                connection.commit()
                            }
                            entity.columns.eachWithIndex { column, index ->
                                Object value = Column.fromString(column, row[index])
                                statement.setObject(index + 1, value)
                            }
                            statement.executeUpdate()
                            ++count
                        }
                        return [status: "OK", count: count]
                    }
                    finally {
                        statement.close()
                    }
                }
                finally {
                    connection.close()
                }
            }
            finally {
                reader.close()
            }
        } catch (Throwable e) {
            log.error("Error while loading CSV into dataset ${entity.name}", e)
            return [status: "ERROR", problems: [e.getMessage()]]
        }
    }
}