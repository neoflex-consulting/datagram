package MetaServer.utils

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import ru.neoflex.meta.model.Database

class ObjectExplorer {
    private final static Log logger = LogFactory.getLog(ObjectExplorer.class);

    private static Map getAllOfType(String typeName) {
        def db = Database.new
        def result = [:]
        db.select("select type(e), e.e_id, e.name from ${typeName} e", [:])
                .collect {[_type_: it[0], e_id: it[1], name: it[2], uid: "${it[0]}[${it[1]}]".toString(), children: []]}
                .each {result[it.uid] = it}
        return result
    }

    private static List getWf2WF() {
        def db = Database.new
        def result = []
        db.select("select type(w), w.e_id, type(e), e.e_id from etl.Workflow w join w.nodes n join n.subWorkflow e where type(n) = 'etl.WFSubWorkflow'", [:])
                .each {result.add(["${it[0]}[${it[1]}]".toString(), "${it[2]}[${it[3]}]".toString()])}
        return result
    }

    private static List getWf2TR() {
        def db = Database.new
        def result = []
        db.select("select type(w), w.e_id, type(e), e.e_id from etl.Workflow w join w.nodes n join n.transformation e where type(n) = 'etl.WFTransformation'", [:])
                .each {result.add(["${it[0]}[${it[1]}]".toString(), "${it[2]}[${it[3]}]".toString()])}
        return result
    }

    private static List getPR2WF() {
        def db = Database.new
        def result = []
        db.select("select type(p), p.e_id, type(w), w.e_id from etl.Workflow w join w.project p", [:])
                .each {result.add(["${it[0]}[${it[1]}]".toString(), "${it[2]}[${it[3]}]".toString()])}
        return result
    }

    private static List getPR2TR() {
        def db = Database.new
        def result = []
        db.select("select type(p), p.e_id, type(w), w.e_id from etl.Transformation w join w.project p", [:])
                .each {result.add(["${it[0]}[${it[1]}]".toString(), "${it[2]}[${it[3]}]".toString()])}
        return result
    }

    private static List getPR2PR() {
        def db = Database.new
        def result = []
        db.select("select type(p), p.e_id, type(w), w.e_id from etl.Project w join w.parentProject p", [:])
                .each {result.add(["${it[0]}[${it[1]}]".toString(), "${it[2]}[${it[3]}]".toString()])}
        return result
    }

    public static Object getRootNode(Map entity, Map params = null) {
        def prs = getAllOfType("etl.Project")
        def wfs = getAllOfType("etl.Workflow")
        def trs = getAllOfType("etl.Transformation")
        def pr2pr = getPR2PR()
        def pr2wf = getPR2WF()
        def pr2tr = getPR2TR()
        def wf2wf = getWf2WF()
        def wf2tr = getWf2TR()
        def ownedpr = pr2pr.collect {it[1]}.toSet()
        def ownedwf = wf2wf.collect {it[1]}.toSet()
        def ownedwf2 = pr2wf.collect {it[1]}.toSet()
        def ownedtr = wf2tr.collect {it[1]}.toSet()
        def ownedtr2 = pr2tr.collect {it[1]}.toSet()
        prs.values().each {pr->pr.children.addAll(pr2pr.findAll {it[0] == pr.uid}.collect {prs[it[1]]}.sort {it.name})}
        prs.values().each {pr->pr.children.addAll(pr2wf.findAll {it[0] == pr.uid && !ownedwf.contains(it[1])}.collect {wfs[it[1]]}.sort {it.name})}
        prs.values().each {pr->pr.children.addAll(pr2tr.findAll {it[0] == pr.uid && !ownedtr.contains(it[1])}.collect {trs[it[1]]}.sort {it.name})}
        wfs.values().each {wf->wf.children.addAll(wf2wf.findAll {it[0] == wf.uid}.collect {wfs[it[1]]}.sort {it.name})}
        wfs.values().each {wf->wf.children.addAll(wf2tr.findAll {it[0] == wf.uid}.collect {trs[it[1]]}.sort {it.name})}
        return [e_id: null, _type_: 'ui3.Module', name: "ETL", children:
                prs.values().findAll {!ownedpr.contains(it.uid)}.sort {it.name} +
                        wfs.values().findAll {!ownedwf.contains(it.uid) && !ownedwf2.contains(it.uid)}.sort {it.name} +
                        trs.values().findAll {!ownedtr.contains(it.uid) && !ownedtr2.contains(it.uid)}.sort {it.name}
        ]
    }

}
