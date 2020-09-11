package MetaServer.utils

/**
 * Created by orlov on 25.12.2015.
 */
class Scala {
    public static String getJavaType(String typeDomain) {
        if (typeDomain == "STRING")
            return "String"
        if (typeDomain == "DECIMAL")
            return "java.math.BigDecimal"
        if (typeDomain == "INTEGER")
            return "java.lang.Integer"
        if (typeDomain == "LONG")
            return "java.lang.Long"
        if (typeDomain == "DATE")
            return "java.sql.Date"
        if (typeDomain == "DATETIME")
            return "java.sql.Timestamp"
        if (typeDomain == "TIME")
            return "java.sql.Timestamp"
        if (typeDomain == "BOOLEAN")
            return "java.lang.Boolean"
        if (typeDomain == "BINARY")
            return "Array[Byte]"
        return "Any"
    }

    public static String makeScript(List inputs, String expression, String type) {
        def script = "(() => {\r\n"
        for (def i = 0; i < inputs.size(); ++i) {
            def input = inputs[i]
            script += "val " + input.name + " : " + input.type + " = null\r\n";
        }
        script += expression != null ? expression : ""
        script += "\r\n}: " + type + ")()"
        return script
    }
}
