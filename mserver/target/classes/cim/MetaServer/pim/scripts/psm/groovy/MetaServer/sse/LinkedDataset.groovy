package MetaServer.sse

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.Context

class LinkedDataset extends AbstractDataset {
    private final static Log log = LogFactory.getLog(Dataset.class)

    LinkedDataset() {
        super()
    }

    LinkedDataset(Map entity) {
        super(entity)
    }

    @Override
    void build(DatasetBuilder builder) {
        Map linkTo = findLinkedDataset(this.db, this.dataset)

        builder.visitRegister(linkTo, this.dataset.shortName);
    }

    static Object fetchData(Map entity, Map params = null) {
        def db = Database.new
        Map linkTo = findLinkedDataset(db, entity)

        if (linkTo == null) {
            log.error("Cannot find linked dataset from ${entity.name}")
            return [status: "ERROR", problems: [entity]]
        }

        def type = ((String) linkTo._type_).split("\\.").last()
        def scriptName = type + ".groovy"
        log.info("Go to link ${linkTo.name}, ${scriptName}")

        try {
            def result = Context.current.contextSvc.scriptSvc.runMethod("MetaServer/sse/" + scriptName, "fetchData", linkTo, params)
            if (result.status == "OK") {
                copyColumns(db, entity, result.columns)
            }
            return result
        } catch (Throwable e) {
            log.error("Error while executing script for ${linkTo.name}, ${scriptName}", e)
            return [status: "ERROR", problems: [e.getMessage()]]
        }
    }

    public static Map findLinkedDataset(Database db, Map entity) {
        def linkTo, linkFrom = db.get(entity), linkCount = 0, MAX_ITERATIONS = 10

        while (linkCount < MAX_ITERATIONS && linkFrom != null) {
            linkTo = db.get(linkFrom.linkTo)
            if (linkTo != null && linkTo._type_ != "sse.LinkedDataset") {
                break
            } else {
                linkFrom = linkTo
                linkTo = null
                linkCount++
            }
        }
        linkTo
    }

    static void copyColumns(Database db, Map entity, List<Map> columns) {
        def ds = db.get(entity)
        ds.columns.clear()
        for (c in columns) {
            ds.columns.add(copyColumn(db, c))
        }

        db.saveOrUpdate(ds)
        db.commit()
    }

    static Object importMetadata(Map entity, Map params = null) {
        def db = Database.new
        Map linkTo = findLinkedDataset(db, entity)

        if (linkTo == null) {
            log.error("Cannot find linked dataset from ${entity.name}")
            return [status: "ERROR", problems: [entity]]
        }

        copyColumns(db, entity, linkTo.columns)

        return [status: "OK", problems: []]
    }
}