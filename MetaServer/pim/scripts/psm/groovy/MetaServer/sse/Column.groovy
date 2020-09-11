package MetaServer.sse

import org.apache.commons.lang.StringUtils
import org.apache.commons.lang.time.DateUtils
import org.springframework.security.crypto.codec.Hex
import ru.neoflex.meta.utils.ECoreUtils

import java.sql.Timestamp
import java.text.SimpleDateFormat

class Column {
    public final static SimpleDateFormat jsonTimestampParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    public final static String[] formats = [
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm.ss'Z'",
            "yyyy-MM-dd'T'HH:mm.ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd' 'HH:mm:ss",
            "yyyy-MM-dd' 'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd",
    ] as String[]
    public static java.util.Date parseDateTime(String valueImg) {
        return DateUtils.parseDate(valueImg, formats)
    }
    public static Object fromString(Map column, String valueImg) {
        if (StringUtils.isEmpty(valueImg)) return null

        if (column.columnType._type_ == "sse.ScalarType") {
            def typeName = column.columnType.dataType.name
            if (typeName == "STRING") return valueImg
            if (typeName == "DECIMAL") return new BigDecimal(valueImg)
            if (typeName == "INTEGER") return Integer.parseInt(valueImg)
            if (typeName == "DATE") return new java.sql.Date(parseDateTime(valueImg).getTime())
            if (typeName == "DATETIME") return new Timestamp(parseDateTime(valueImg).getTime())
            if (typeName == "TIME") return new Timestamp(parseDateTime(valueImg).getTime())
            if (typeName == "BINARY") return Hex.decode(valueImg)
            if (typeName == "BOOLEAN") return Boolean.parseBoolean(valueImg)
            if (typeName == "LONG") return Long.parseLong(valueImg)
            if (typeName == "FLOAT") return Float.parseFloat(valueImg)
            if (typeName == "DOUBLE") return Double.parseDouble(valueImg)
        }
        return null
    }

    public static Object encode(Map column, Object value) {
        if (value == null) return null

        if (column.columnType._type_ == "sse.ScalarType") {
            def typeName = column.columnType.dataType
            if (typeName == "BINARY") return Hex.encode(value as byte[])
        }
        return value
    }
}
