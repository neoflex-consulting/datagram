package MetaServer.sse

import MetaServer.utils.JDBC
import com.google.common.base.Strings
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.Context

import java.util.regex.Pattern

class QueryDataset extends JdbcDataset {

    QueryDataset() {
        super()
    }

    QueryDataset(Map entity) {
        super(entity)
    }

    static void updateDsMetadata(Map entity, List cols) {
        def db = Database.new
        def ds = db.get(entity)

        ds.columns?.clear()
        for (c in cols) {
            def params = [columnName: c.name,
                          columnType: "ScalarType",
                          isNullable: (c.nullable != 0),
                          typ       : rel2DataType[c.dataType._type_]
            ]
            def column = createColumn(db, params)
            ds.columns.add(column)
        }
        db.saveOrUpdate(ds)
        db.commit()
    }

    static Object fetchData(Map entity, Map params = null) {
        Integer batchSize = Integer.valueOf(params?.batchSize ?: "1000")

        def cn = params?.connection ?: entity.connection
        def query = params?.query ?: entity.query

        if (cn == null || Strings.isNullOrEmpty(query)) {
            log.error("Cannot find connection/query on dataset: " + entity.name)
            return [status: "ERROR", problems: ["Empty connection/query"]]
        }

        cn = Database.new.get(cn)

        query = interpolateParameters(entity.workspace, query)
        log.info("Interpolated query ${query}")

        // В дальнейшем - перевод на асинхронную отдачу данных
        try {
            def result = JDBC.select(Context.current, cn, query, batchSize)
            if (entity.e_id != null &&
                    (entity.columns == null
                    || entity.columns.size() == 0
                    || entity.columns.size() != result.columns.size()
                    // в случае, если у нас запрос передан как параметр, и он пока еще не совпадает с тем что в сущности,
                    // то структуру не обновляем
                    || (params?.query != null && params?.query == entity.query))) {
                updateDsMetadata(entity, result.columns)
            }
            return [status: "OK", columns: result.columns.collect { c -> [columnName: c.name] }, rows: result.rows]
        } catch (Throwable e) {
            log.error("Error while query dataset ${entity.name}", e)
            return [status: "ERROR", problems: [e.toString()]]
        }

    }

    private static String interpolateParameters(Map workspace, String sql) {
        def matcher = Pattern.compile("[&][a-zA-Z_][a-zA-Z\\d_]*").matcher(sql)
        def params = new HashSet<String>()
        while (matcher.find()) {
            params.add(sql.substring(matcher.start() + 1, matcher.end()))
        }

        def jobParams = Workspace.getWorkspaceParameters(workspace)

        for (param in params) {
            sql = sql.replace('&' + param, '\'' + jobParams.getOrDefault(param, '') + '\'')
        }
        return sql
    }

}