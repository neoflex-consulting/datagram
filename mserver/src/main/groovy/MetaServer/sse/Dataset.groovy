package MetaServer.sse

import MetaServer.utils.EMF
import com.google.common.base.Strings
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import ru.neoflex.meta.model.Database

class Dataset extends AbstractDataset {
    private final static Log log = LogFactory.getLog(Dataset.class)

    Dataset() {
        super()
    }

    Dataset(Map entity) {
        super(entity)
    }

    @Override
    void build(DatasetBuilder builder) {
        if (builder.isFullRebuild || this == builder.root) {
            for (AbstractDataset ds : prepareDatasets(this.db, this.dataset).collect { it -> datasetFactory(it as Map) }) {
                ds.build(builder)
            }
            builder.visitBuild(this.dataset)
        }
        builder.visitRegister(this.dataset, this.dataset.shortName)
    }

    static Object build(Map entity, Map params = null) {

        if (Strings.isNullOrEmpty(entity.expression)) {
            log.error("Nothing to run")
            return [status: "ERROR", problems: [[ename    : "Nothing to run",
                                                 evalue   : "",
                                                 traceback: []
                                                ]]]
        }

        AbstractDataset root = datasetFactory(entity)
        boolean fullRebuild = Boolean.parseBoolean(params.fullRebuild ?: "false")
        List<String> allCode = new DatasetBuilder().build(root, fullRebuild, params)

        try {
            def result = runCode(entity, String.join("\n", allCode))
            HiveDataset.updateDsMetadata(entity, [:])
            return result
        } catch (Throwable e) {
            log.error("Error while build dataset ${entity.name}", e)
            return [status: "ERROR", problems: [[ename    : "Error while build dataset",
                                                 evalue   : e.getMessage(),
                                                 traceback: []
                                                ]]]
        }
    }

    static Object executeQuery(Map entity, Map params = null) {
        return internalExecute(entity, params, "/pim/dataspace/execute.egl")
    }

    static Object fetchData(Map entity, Map params = null) {
        return HiveDataset.fetchData(entity, params)
    }

    private static Object internalExecute(Map entity, Map params, String template) {
        def db = Database.new
        def datasets = prepareDatasets(db, entity)

        def limit = params.limit ?: params.batchSize ?: "100"
        params.limit = limit

        if (Strings.isNullOrEmpty(entity.expression)) {
            log.error("Nothing to run")
            return [status: "ERROR", problems: [[ename    : "Nothing to run",
                                                 evalue   : "",
                                                 traceback: []
                                                ]]]
        }

        try {
            def code = genCode(template, entity, datasets, params)

            return runCode(entity, code)
        } catch (Throwable e) {
            log.error("Error while build dataset ${entity.name}", e)
            return [status: "ERROR", problems: [[ename    : "Error while build dataset",
                                                 evalue   : e.getMessage(),
                                                 traceback: []
                                                ]]]
        }
    }

    private static String genCode(String template, Map dataset, List datasets, Map params) {
        params.dataset = dataset
        params.dataset.interpreter = dataset.interpreter ?: "SQL"
        params.datasets = datasets
        def ds = []
        ds.addAll(datasets)
        ds.add(dataset.workspace)
        return EMF.generate(ds, template, params)
    }


    private static List prepareDatasets(Database db, Map entity) {
        def datasets = []
        for (dsName in (entity.datasets ?: [])) {
            def ds = db.select("from sse.AbstractDataset where name = :name", [name: dsName.name])
            if (ds.size() > 0) {
                def dsf = ds.get(0)
                datasets.add(dsf)
            }
        }
        datasets
    }

    static Object fullRebuild(Map entity, Map params = null) {
        return build(Database.new.get(entity), [fullRebuild: "true"])
    }

    static Object scheduleFullRebuild(Map entity, Map params = null) {
        def db = Database.new
        def dataset = db.get(entity)
        def scheduledTasks = db.list("rt.ScheduledTask", [entityType: dataset._type_, entityName: dataset.name, methodName: "fullRebuild"])
        if (scheduledTasks.size() > 0) {
            def scheduledTask = scheduledTasks.get(0)
            return [status: "Already Defined", scheduledTask: [e_id: scheduledTask.e_id, _type_: scheduledTask._type_, name: scheduledTask.name]]
        }
        def scheduledTask = db.instantiate("rt.ScheduledTask", [
                name: "sse.Dataset." + dataset.name + ".fullRebuild",
                entityType: dataset._type_,
                entityName: dataset.name,
                methodName: "fullRebuild",
                scheduler: db.instantiate("rt.CronScheduler", [cronExpression: "0 0 * * * *"])
        ])
        db.save(scheduledTask)
        return [status: "Created", scheduledTask: [e_id: scheduledTask.e_id, _type_: scheduledTask._type_, name: scheduledTask.name]]
    }
}