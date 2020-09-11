package MetaServer.sse

import MetaServer.rt.LivyServer
import com.google.common.base.Strings
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.JSONHelper

abstract class AbstractDataset {
    private final static Log log = LogFactory.getLog(AbstractDataset.class)

    final Map dataset
    protected final Database db = Database.new

    protected static List columns2Json(Map ds) {
        return JSONHelper.toJSON("teneo", (String) ds._type_, ds).columns
    }

    AbstractDataset() {
        this.dataset = [:]
    }

    AbstractDataset(Map entity) {
        this.dataset = entity
    }

    abstract void build(DatasetBuilder builder)

    static AbstractDataset datasetFactory(Map entity) {
        switch (entity._type_) {
            case "sse.Dataset": return new Dataset(entity)
            case "sse.HiveDataset": return new HiveDataset(entity)
            case "sse.HiveExternalDataset": return new HiveExternalDataset(entity)
            case "sse.LinkedDataset": return new LinkedDataset(entity)
            case "sse.QueryDataset": return new QueryDataset(entity)
            case "sse.ReferenceDataset": return new ReferenceDataset(entity)
            case "sse.TableDataset": return new TableDataset(entity)
            default: throw new  IllegalArgumentException("Unsupported dataset type: " + entity._type_)
        }
    }

    static Map createColumn(Database db, Map params) {
        def columnName = ((String)params.columnName).split("\\.").last()
        if (Strings.isNullOrEmpty(columnName)) {
            throw new IllegalArgumentException("name of column is empty")
        }

        def column = db.instantiate("sse.Column", [columnName: columnName])

        column.columnType = createColumnType(db, params)
        return column
    }

    static Map copyColumn(Database db, Map c) {
        def column = db.instantiate("sse.Column", [columnName: c.columnName])

        column.columnType = createColumnType(db, c.columnType)
        return column
    }

    static Object runCode(Map entity, String code) {
        def livy = Workspace.getLivyServer(entity.workspace)

        if (livy == null) {
            log.error("Cannot find livySerer to run code")
            return [status: "ERROR", problems: [[ename    : "Cannot find livySerer to run code",
                                                 evalue   : "",
                                                 traceback: []
                                                ]]]
        }

        def result = LivyServer.runCode(livy, [code: code, kind: "scala"])
        if (result.output.status == 'error') {
            log.error(result.output.evalue)
            return [status: "ERROR", problems: [[ename    : result.output.ename,
                                                 evalue   : result.output.evalue,
                                                 traceback: result.output.traceback
                                                ]]]
        } else {
            result = result.output.data
            def jsonData = "{fields:[]}"
            if (result != null) {
                def parsedJson = (result instanceof Map) ? (result.values()[0] =~ /(?ms).*^(\{.*\})$.*/) : (result =~ /\{.*\}/)
                if (parsedJson.matches()) {
                    jsonData = parsedJson.group(1)
                }
            }
            return [status: "OK", result: jsonData]
        }
    }

    private static Map<String, Object> createColumnType(Database db, Map params) {
        String colType = params.columnType ?: params._type_.split("\\.").last()
        if (Strings.isNullOrEmpty(colType)) {
            throw new IllegalArgumentException("Error: unspecified column type")
        }

        def columnType = db.instantiate("sse." + colType)
        columnType.isNullable = params.isNullable
        columnType.description = params.description

        switch (colType) {
            case "ScalarType": fillScalarColumnType(columnType, params); break
            case "ArrayType": fillArrayColumnType(db, columnType, params); break
            case "StructType": fillStructColumnType(db, columnType, params); break
            default: throw new IllegalArgumentException("Unsupported column type: " + colType)
        }
        columnType
    }

    private static void fillScalarColumnType(Map columnType, Map params) {
        columnType.dataType = JSONHelper.getEnumerator("teneo", "sse.ScalarType", "dataType", params.typ)
        columnType.length = params.length
        columnType.precision = params.precision
        columnType.nativeType = params.nativeType
    }

    private static void fillArrayColumnType(Database db, Map columnType, Map params) {
        columnType.elementType = createColumnType(db, (Map) params.elementType)
    }

    private static void fillStructColumnType(Database db, Map columnType, Map params) {
        def innerColumns = []
        for (Map c in params.columns) {
            def column = createColumn(db, c)
            innerColumns.add(column)
        }
        columnType.columns = innerColumns
    }
}