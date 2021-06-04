package MetaServer.sse

import MetaServer.utils.JDBC
import com.google.common.base.Strings
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.apache.hadoop.hive.conf.HiveConf
import org.apache.hadoop.hive.serde2.typeinfo.BaseCharTypeInfo
import org.apache.hadoop.hive.serde2.typeinfo.DecimalTypeInfo
import org.apache.hive.hcatalog.api.HCatClient
import org.apache.hive.hcatalog.api.HCatTable
import org.apache.hive.hcatalog.data.schema.HCatFieldSchema
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.Context

import static org.apache.hive.hcatalog.data.schema.HCatFieldSchema.Category.*

class HiveDataset extends AbstractDataset {
    private final static Log log = LogFactory.getLog(HiveDataset.class)

    final static Map<String, String> hive2DataType = [
            "TINYINT": "INTEGER",
            "SMALLINT": "INTEGER",
            "INT": "INTEGER",
            "INTEGER": "INTEGER",
            "BIGINT": "LONG",
            "FLOAT": "FLOAT",
            "DOUBLE": "DOUBLE",
            "DOUBLE PRECISION": "DOUBLE",
            "DECIMAL": "DECIMAL",
            "NUMERIC": "DECIMAL",
            "TIMESTAMP": "DATETIME",
            "DATE": "DATE",
            "INTERVAL": "UNSUPPORTED",
            "STRING": "STRING",
            "VARCHAR": "STRING",
            "CHAR": "STRING",
            "BOOLEAN": "BOOLEAN",
            "BINARY": "BINARY"
    ]

    HiveDataset() {
        super()
    }

    HiveDataset(Map entity) {
        super(entity)
    }

    @Override
    void build(DatasetBuilder builder) {
        builder.visitRegister(this.dataset, this.dataset.shortName)
    }

// FIXME копипаста с TableDataset/QueryDataset. В дальнейшем, будет различаться???
    static Object fetchData(Map entity, Map params = null) {
        Integer batchSize = Integer.valueOf(params?.batchSize ?: "1000")

        def db = Database.new
        def ds = db.get(entity)
        def cn = Workspace.getHiveConnection(ds.workspace)
        String hiveDb = ds.db ?: ds.workspace.name
        String table = ds.table ?: ds.shortName
        def query = "select * from ${hiveDb}.${table}"

        if (!Strings.isNullOrEmpty(params?.filter)) {
            query = "select t.* from (${query}) t where ${params.filter}"
        }

        if (cn == null || Strings.isNullOrEmpty(hiveDb) || Strings.isNullOrEmpty(table)) {
            log.error("Cannot find connection/query on dataset: ${ds.name}")
            return [status: "ERROR", problems: ["Empty connection/query"]]
        }

        // В дальнейшем - перевод на асинхронную отдачу данных
        try {
            log.debug("Executing query: ${query}")
            def result = JDBC.select(Context.current, cn, query, batchSize)

            if (ds.columns == null || ds.columns.size() == 0 || ds.columns.size() != result.columns.size()) {
                updateDsMetadata(ds, [:])
            }
            return [status: "OK", columns: columns2Json(ds), rows: result.rows]
        } catch (Throwable e) {
            log.error("Error while query dataset ${ds.name}", e)
            return [status: "ERROR", problems: [e.getMessage()]]
        }
    }

    static Object updateDsMetadata(Map entity, Map params = null) {
        Database db = Database.new
        Map ds = db.get(entity)
        String hiveUri = Workspace.getHiveMetastoreUri(ds.workspace)
        // logic for HiveDs vs Ds
        String hiveDb = ds.db ?: ds.workspace.name
        String table = ds.table ?: ds.shortName
        String catalog = ds.workspace.cluster.hiveCatalog
        String dbPrefix = ""
        if (catalog != null && !catalog.isEmpty()) {
            dbPrefix = "@" + catalog + "#"
        }

        if (Strings.isNullOrEmpty(hiveUri) || Strings.isNullOrEmpty(hiveDb) || Strings.isNullOrEmpty(table)) {
            log.error("Cannot find connection/query on dataset: ${ds.name}")
            return [status: "ERROR", problems: ["Empty connection/query"]]
        }

        try {
            HiveConf conf = new HiveConf()
            conf.setVar(HiveConf.ConfVars.METASTOREURIS, hiveUri)
            conf.set("hive.metastore.local", "false")

            HCatClient client = HCatClient.create(conf)
            HCatTable hTable = client.getTable(dbPrefix + hiveDb, table)

            def cols = hTable.getCols()
            cols.addAll(hTable.getPartCols())
            ds.columns?.clear()
            for (c in cols) {
                def column = createColumn(db, fieldSchemaParse(c))
                ds.columns.add(column)
            }

            def partByCols = []
            for (c in hTable.getPartCols()) {
                partByCols.add(c.name.split("\\.").last())
            }

            ds.partitionByCols = partByCols

            db.saveOrUpdate(ds)
            db.commit()
        } catch (Throwable e) {
            log.error("Error while connect for maetatdta ${ds.name}", e)
            return [status: "ERROR", problems: [e.getMessage()]]
        }
        return [status: "OK", problems: []]
    }

    private static Map fieldSchemaParse(HCatFieldSchema c) {
        def colParams = [columnName: c.name,
                         desciption: c.comment,
                         isNullable: true,
                         nativeType: c.typeString
        ]

        if (c.category == PRIMITIVE) {
            colParams.columnType = "ScalarType"
            def ti = c.typeInfo
            colParams.typ = hive2DataType[ti.typeName.toUpperCase()]
            if (ti instanceof BaseCharTypeInfo) {
                colParams.length = (ti as BaseCharTypeInfo).length
            }
            if (ti instanceof DecimalTypeInfo) {
                colParams.length = (ti as DecimalTypeInfo).scale
                colParams.precision = (ti as DecimalTypeInfo).precision
            }
        }

        if (c.category == ARRAY) {
            colParams.columnType = "ArrayType"
            def arrSchema = c.arrayElementSchema
            if (arrSchema.fields.size() > 1) {
                log.warn("Strange schema for array element for ${c.name} column")
            }
            colParams.elementType = fieldSchemaParse(arrSchema.get(0)) // FIXME Насколько это законно?
            colParams.typ = "ARRAY"
        }

        if (c.category == STRUCT) {
            colParams.columnType = "StructType"
            colParams.columns = []
            for (ci in c.structSubSchema.fields) {
                colParams.columns.add(fieldSchemaParse(ci))
            }
            colParams.typ = "STRUCT"
        }

        return colParams
    }

    static Object importMetadata(Map entity, Map params = null) {
        return updateDsMetadata(entity, params)
    }
}