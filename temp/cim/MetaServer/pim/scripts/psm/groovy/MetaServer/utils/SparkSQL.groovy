package MetaServer.utils

import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan
import org.apache.spark.sql.execution.SparkSqlParser
import org.apache.spark.sql.internal.SQLConf

class SparkSQL {
    private static final ThreadLocal<Map> planCache = new ThreadLocal<Map>(){
        protected Map initialValue() {
            return [:]
        }
    }

    static void clearPlanCache() {
        planCache.get().clear()
    }
    private static String interpolareParameters(String sql) {
        def matcher = java.util.regex.Pattern.compile("[&][a-zA-Z_][a-zA-Z\\d_]*").matcher(sql)
        def params = []
        while (matcher.find()) {
            params.add(sql.substring(matcher.start() + 1, matcher.end()))
        }
        for (param in params) {
            sql = sql.replace("&" + param, '"' + param + '"')
        }
        return sql
    }

    private static List collectNodes(simpleName, node, list) {
        if (node.class.simpleName == simpleName) {
            list.add(node)

        }
        def iter = node.children().iterator()
        while (iter.hasNext()) {
            collectNodes(simpleName, iter.next(), list)
        }
        return list
    }

    private static List getUnresolvedAttributes(node, list) {
        return collectNodes("UnresolvedAttribute", node, list).collect {
            def i = it.nameParts.iterator()
            def alias = i.next()
            if (!i.hasNext()) {
                return [alias: null, name: alias]
            }
            def name = i.next()
            return [alias: alias, name: name]
        }
    }

    private static List getUnresolvedRelations(node, list) {
        return collectNodes("UnresolvedRelation", node, list).collect {
            return it.tableIdentifier.identifier
        }
    }

    private static Map extractDepsFromPlan(plan) {
        def result = [:]
        if (plan.class.simpleName == "Project") {
            result = extractDepsFromProject(plan)
        }
        else if (plan.class.simpleName == "Distinct") {
            result = extractDepsFromPlan(plan.child)
        }
        else if (plan.class.simpleName == "Union") {
            def iter = plan.children().iterator()
            while (iter.hasNext()) {
                def dep = extractDepsFromPlan(iter.next())
                dep.keySet().each {
                    def deps = result[it] as List
                    if (deps == null) {
                        deps = []
                        result[it] = deps
                    }
                    deps.addAll(dep[it])
                }
            }
        }
        else if (plan.class.simpleName == "With") {
            def cte = [:]
            def iter = plan.cteRelations.iterator()
            while (iter.hasNext()) {
                def rel = iter.next()
                def name = rel._1
                def subquery = rel._2
                def cteDep = extractDepsFromPlan(subquery.child)
                cte[name] = cteDep
            }
            result = extractDepsFromPlan(plan.child)
            result.keySet().each {
                result[it] = result[it].collect {
                    def cteDep = cte[it.alias]
                    if (cteDep != null) {
                        def depList = cteDep[it.name]
                        if (depList == null) {
                            def starDep = cteDep["*"]
                            if (starDep == null) {
                                return it
                            }
                            return starDep.collect {star->[alias: star.alias, name: it.name]}
                        }
                        else {
                            return depList
                        }
                    }
                    else {
                        return it
                    }
                } .flatten()
            }
        }
        else if (plan.class.simpleName == "Aggregate") {
            extractAttributes(result, plan.aggregateExpressions)
            extractTables(result, plan.child)
        }
        else if (plan.class.simpleName == "Filter") {
            result = extractDepsFromPlan(plan.child)
        }
        else {
            println("Unknown plan " + plan.class.simpleName)
            result = extractDepsFromGeneric(plan)
        }
        result.keySet().each {
            result[it] = (result[it] as List).unique()
        }
        return result
    }
    private static void walk(tree, walker) {
        if (walker(tree)) {
            def iter = tree.children().iterator()
            while (iter.hasNext()) {
                walk(iter.next(), walker)
            }
        }
    }
    private static Map extractDepsFromGeneric(node) {
        def result = [:]
        walk(node, {child->
            if (child.class.simpleName == "SubqueryAlias") {
                def alias = child.alias
                def table = child.child.tableIdentifier.identifier
                result.keySet().each {
                    result[it].each {
                        if (it.alias.equalsIgnoreCase(alias)) {
                            it.alias = table
                        }
                    }
                }
                return false
            }
            else if (child.class.simpleName == "UnresolvedRelation") {
                def table = child.tableIdentifier.identifier
                result.keySet().each {
                    result[it].each {
                        if (it.alias == null) {
                            it.alias = table
                        }
                    }
                }
                return false
            }
            else if (child.class.simpleName == "Alias") {
                def name = child.name
                result[name] = getUnresolvedAttributes(child.child, [])
                return false
            }
            else if (child.class.simpleName == "UnresolvedAttribute") {
                def list = getUnresolvedAttributes(child, [])
                result[list.first().name] = list
                return false
            }
            else if (child.class.simpleName == "UnresolvedStar") {
                result["*"] = [[alias: col.target.x.iterator().next(), name: "*"]]
                return false
            }
            return true
        })
        return result
    }
    private static Map extractTables(Map result, node) {
        walk(node, {child->
            if (child.class.simpleName == "SubqueryAlias") {
                def alias = child.alias
                def table = child.child.tableIdentifier.identifier
                result.keySet().each {
                    result[it].each {
                        if (it.alias.equalsIgnoreCase(alias)) {
                            it.alias = table
                        }
                    }
                }
                return false
            }
            else if (child.class.simpleName == "UnresolvedRelation") {
                def table = child.tableIdentifier.identifier
                result.keySet().each {
                    result[it].each {
                        if (it.alias == null) {
                            it.alias = table
                        }
                    }
                }
                return false
            }
            return true
        })
        return result
    }
    private static Map extractAttributes(Map result, attList) {
        def iter = attList.iterator()
        while (iter.hasNext()) {
            def col = iter.next()
            if (col.class.simpleName == "Alias") {
                def name = col.name
                result[name] = getUnresolvedAttributes(col.child, [])
            }
            else if (col.class.simpleName == "UnresolvedAttribute") {
                def list = getUnresolvedAttributes(col, [])
                result[list.first().name] = list
            }
            else if (col.class.simpleName == "UnresolvedStar") {
                result["*"] = (result["*"] ?: []) + [[alias: col.target.x.iterator().next(), name: "*"]]
            }
            else {
                println("unknown col: " + col.toString())
            }
        }
        return result
    }
    private static Map extractDepsFromProject(project) {
        def result = [:]
        extractAttributes(result, project.projectList)
        extractTables(result, project.child)
        return result
    }
    static LogicalPlan parse(String sqlText) {
        SparkSqlParser parser = new SparkSqlParser(new SQLConf());
        return parser.parsePlan(sqlText);
    }

    static extractDepsFromSQL(String sqlText) {
        def deps = planCache.get()[sqlText]
        if (deps == null) {
            def plan = parse(interpolareParameters(sqlText))
            deps = extractDepsFromPlan(plan)
            planCache.get()[sqlText] = deps
        }
        return deps
    }
}
