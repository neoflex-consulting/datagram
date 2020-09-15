package MetaServer.sse

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import java.sql.Types

abstract class JdbcDataset extends AbstractDataset {
    protected final static Log log = LogFactory.getLog(JdbcDataset.class)

    protected static String jdbc2DataType(int type) {
        switch(type) {
            case Types.NUMERIC: return "NUMERIC"
            case Types.DECIMAL: return "DECIMAL"
            case Types.BIT: return "BOOLEAN"
            case Types.TINYINT: return "TINYINT"
            case Types.SMALLINT: return "SMALLINT"
            case Types.INTEGER: return "INTEGER"
            case Types.BIGINT: return "LONG"
            case Types.REAL: return "REAL"
            case Types.FLOAT: return "FLOAT"
            case Types.DOUBLE: return "DOUBLE"
            case Types.BINARY: return "BINARY"
            case Types.VARBINARY: return "BINARY"
            case Types.LONGVARBINARY: return "LONGVARBINARY"
            case Types.DATE: return "DATE"
            case Types.TIME: return "TIME"
            case Types.TIMESTAMP: return "DATETIME"
            case Types.BLOB: return "BLOB"
            case Types.CLOB: return "CLOB"
            default: return null
        }
    }

    public final static Map<String, String> rel2DataType = [
            "rel.CHAR"    : "STRING",
            "rel.VARCHAR" : "STRING",
            "rel.DATETIME": "DATETIME",
            "rel.INTEGER" : "INTEGER",
            "rel.LONG"    : "LONG",
            "rel.DECIMAL" : "DECIMAL",
            "rel.DATE"    : "DATE",
            "rel.TIME"    : "TIME",
            "rel.BLOB"    : "BINARY",
            "rel.BOOLEAN" : "BOOLEAN",
            "rel.XML"     : "BINARY",
            "rel.FLOAT"   : "FLOAT",
            "rel.DOUBLE"  : "DOUBLE"
    ]

    JdbcDataset() {
        super()
    }

    JdbcDataset(Map entity) {
        super(entity)
    }

    @Override
    void build(DatasetBuilder builder) {
        builder.visitRegister(this.dataset, this.dataset.shortName)
    }
}
