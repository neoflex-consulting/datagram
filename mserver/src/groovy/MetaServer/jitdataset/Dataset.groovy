package MetaServer.jitdataset
import ru.neoflex.meta.utils.Context
/* protected region MetaServer.jitdatasetDataset.inport on begin */
import MetaServer.rt.TransformationDeployment
import MetaServer.utils.HDFSClient
import MetaServer.etl.Project
import MetaServer.utils.EMF
import MetaServer.utils.JDBC
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import ru.neoflex.meta.model.Database
/* protected region MetaServer.jitdatasetDataset.inport end */
class Dataset {
    /* protected region MetaServer.jitdatasetDataset.statics on begin */
    private final static Log logger = LogFactory.getLog(Dataset.class)

    static boolean tableExists(String tableName, String schema, connection) {
        def tables = JDBC.selectAfterUpdates(Context.current, connection, "show tables", -1, ["use ${schema}"])["rows"]
        tables.any {tableName.equalsIgnoreCase(it[0])}
    }

    static void createTable(Map dataset, Map deployment, String suffix, String tmpl) {
        def schema = deployment.hiveDatabaseName
        def tableName = dataset.name.toLowerCase() + suffix
        if (tableExists(tableName, schema, deployment.hiveConnection)) {
            logger.info("table ${tableName} already exists, dropping...")
        }
        def sqls = [
                "drop table if exists ${schema}.${tableName}",
                EMF.generate([dataset, deployment], tmpl, [suffix: suffix]),
                "MSCK REPAIR TABLE ${schema}.${tableName}"
        ]
        sqls.each {logger.info(it)}
        def rcs = JDBC.updateList(Context.current, deployment.hiveConnection, sqls)
    }

    static Object repairTable(Map dataset, Map deployment, List suffixes) {
        def sqls = suffixes.collect {
            "MSCK REPAIR TABLE ${deployment.hiveDatabaseName}.${dataset.name.toLowerCase()}${it}".toString()
        }
        sqls.each {logger.info(it)}
        return JDBC.updateList(Context.current, deployment.hiveConnection, sqls)
    }

    static void createTable(Map dataset, Map deployment) {
        createTable(dataset, deployment, "", "pim/jitrep/psm/hive/createDatasetTable.egl")
        createTable(dataset, deployment, "_ld", "pim/jitrep/psm/hive/createDatasetTableForLoad.egl")
        createTable(dataset, deployment, "_res", "pim/jitrep/psm/hive/createDatasetTableResult.egl")
    }

    static void createTransformationCls(Map dataset, Map deployment) {
        if (dataset.dataFields.any {it._type_ == "jitdataset.ClassifiedField"}) {
            createTrandformation('tr_jitrep_cls_', dataset, deployment, "pim/jitrep/createClassifyTransformation.etl")
            String drl = EMF.generate([dataset, deployment], "pim/jitrep/psm/drools/createClassifyRules.egl", [:])
            logger.info("Classification rules:\n" + drl)
            def hdfs = new HDFSClient(deployment.webHdfsUtl, deployment.hdfsUser)
            def fileName = deployment.hdfsBaseDirectory + "/" + deployment.hdfsUser + "/rules/" + dataset.name.toLowerCase() + ".drl"
            try {
                hdfs.delete(fileName)
            }
            catch (Throwable th) {

            }
            hdfs.putString(fileName, drl)
        }
    }

    static void createTransformations(Map dataset, Map deployment) {
        createTrandformation('tr_jitrep_csv_', dataset, deployment, "pim/jitrep/createCSVLoadTransformation.etl")
        if (dataset.buildSpec != null) {
            createTrandformation('tr_jitrep_spc_', dataset, deployment, "pim/jitrep/createSpecLoadTransformation.etl")
        }
        createTransformationCls(dataset, deployment)
        for (publication in dataset.publications) {
            createTrandformation('tr_jitrep_exp_' + publication.name + "_", dataset, deployment, "pim/jitrep/createExportTransformation.etl", false)
        }
    }

    private static void createTrandformation(String prefix, Map dataset, Map deployment, String src) {
        createTrandformation(prefix, dataset, deployment, src, true)
    }

    private static void createTrandformation(String prefix, Map dataset, Map deployment, String src, boolean deleteIfExists) {
        def trName = prefix + dataset.name.toLowerCase()
        def db = Database.new
        def transformations = db.session.createQuery("select tr from etl.Transformation tr where tr.name='${trName}'").list()
        for (old in transformations) {
            logger.info("Found transformation ${trName}(e_id=${old.e_id})".toString())
            if (!deleteIfExists) {
                return
            }
            def transformationDeployments = db.session.createQuery("select tr from rt.TransformationDeployment tr where tr.transformation.e_id=${old.e_id}").list()
            for (oldDep in transformationDeployments) {
                logger.info("Found transformation deployment ${oldDep.name}(e_id=${oldDep.e_id}). Deleting...".toString())
                db.delete(oldDep._type_, oldDep)
            }
            db.delete(old._type_, old)
        }
        logger.info("Create transformation ${trName}".toString())
        EMF.transform([dataset, deployment], src, [prefix: prefix])
        for (newTr in db.session.createQuery("select tr from etl.Transformation tr where tr.name='${trName}'").list()) {
            Project.setEntityJsonView(newTr, true)
            db.save(newTr)
        }
    }

    static void activateInt(Map dataset, Map deployment) {
        createTable(dataset, deployment)
        createTransformations(dataset, deployment)
    }

    static List buildTransformation(String prefix, Map dataset) {
        def result = []
        def db = Database.new
        for (transformation in db.list("etl.Transformation", [name: prefix + dataset.name.toLowerCase()])) {
            def trdList = db.session.createQuery("from rt.TransformationDeployment where transformation.e_id=${transformation.e_id}").list()
            if (trdList.size() != 1) {
                throw new RuntimeException("Multiple or none TransformationDeployment for Transformation ${transformation.name} found")
            }
            def trd = trdList.get(0)
            TransformationDeployment.install(trd)
            result.add(transformation.name)
        }
        return result
    }

    static Object buildAllTransformations(Map dataset, Map params = null) {
        def transformations = []
        for (prefix in ['tr_jitrep_csv_', 'tr_jitrep_spc_', 'tr_jitrep_cls_']) {
            transformations += buildTransformation(prefix, dataset)
        }
        return [result: true, problems:[], transformations: transformations]
    }

    static Map runTransformation(String prefix, Map dataset, Map params) {
        def db = Database.new
        def transformationName = prefix + dataset.name.toLowerCase()
        for (transformation in db.list("etl.Transformation", [name: transformationName])) {
            def trdList = db.session.createQuery("from rt.TransformationDeployment where transformation.e_id=${transformation.e_id}").list()
            if (trdList.size() != 1) {
                throw new RuntimeException("Multiple or none TransformationDeployment for Transformation ${transformation.name} found")
            }
            def trd = trdList.get(0)
            return TransformationDeployment.generateAndRunNoWait(trd, params)
        }
        throw new RuntimeException("Transformation ${transformationName} not found")
    }

    private static Map getDeploymentDescriptor(Database db, Map entity) {
        def deployments = db.session.createQuery("select dd from jitdataset.DeploymentDescriptor dd join dd.datasets dataset where dataset.e_id=${entity.e_id}").list()
        if (deployments.size() != 1) {
            throw new RuntimeException("Multiple or none DatasetDeployment for Dataset ${entity.name} found")
        }
        def deployment = deployments.get(0)
        deployment
    }

    /* protected region MetaServer.jitdatasetDataset.statics end */

    static Object validate(Map entity, Map params = null) {
    /* protected region MetaServer.jitdatasetDataset.validate on begin */
        return EMF.validate(Database.new.get(entity), "/pim/jitrep/jitdataset.evl")
    /* protected region MetaServer.jitdatasetDataset.validate end */
    }

    static Object activate(Map entity, Map params = null) {
    /* protected region MetaServer.jitdatasetDataset.activate on begin */
        def db = Database.new
        def deployment = getDeploymentDescriptor(db, entity)
        def dataset = db.get(entity)
        activateInt(dataset, deployment)
        return [result: true, problems: []]
    /* protected region MetaServer.jitdatasetDataset.activate end */
    }

    static Object activateClassification(Map entity, Map params = null) {
    /* protected region MetaServer.jitdatasetDataset.activateClassification on begin */
        def db = Database.new
        def deployment = getDeploymentDescriptor(db, entity)
        def dataset = db.get(entity)
        createTransformationCls(dataset, deployment)
        return [result: true, problems: []]
    /* protected region MetaServer.jitdatasetDataset.activateClassification end */
    }

    static Object loadFromFile(Map entity, Map params = null) {
    /* protected region MetaServer.jitdatasetDataset.loadFromFile on begin */
        return runTransformation('tr_jitrep_csv_', entity, params)
    /* protected region MetaServer.jitdatasetDataset.loadFromFile end */
    }

    static Object build(Map entity, Map params = null) {
    /* protected region MetaServer.jitdatasetDataset.build on begin */
        return runTransformation('tr_jitrep_spc_', entity, params)
    /* protected region MetaServer.jitdatasetDataset.build end */
    }

    static Object classify(Map entity, Map params = null) {
    /* protected region MetaServer.jitdatasetDataset.classify on begin */
        return runTransformation('tr_jitrep_cls_', entity, params)
    /* protected region MetaServer.jitdatasetDataset.classify end */
    }

    static Object repairTables(Map entity, Map params = null) {
    /* protected region MetaServer.jitdatasetDataset.repairTables on begin */
        return repairTable(entity, getDeploymentDescriptor(Database.new, entity), ["_ld", "", "_res"])
    /* protected region MetaServer.jitdatasetDataset.repairTables end */
    }

    static Object export(Map entity, Map params = null) {
    /* protected region MetaServer.jitdatasetDataset.exportCurrentBranch on begin */
        return runTransformation('tr_jitrep_exp_' + params.name + '_', entity, params)
    /* protected region MetaServer.jitdatasetDataset.exportCurrentBranch end */
    }
}
