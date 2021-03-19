package MetaServer.etl

import MetaServer.rt.LivyServer
import MetaServer.utils.ECoreHelper
import MetaServer.utils.EMF
import MetaServer.utils.GenerationBase
import MetaServer.utils.MClass
import MetaServer.utils.MetaInfo
import org.apache.commons.lang.StringUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import org.eclipse.emf.ecore.util.EcoreUtil
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.svc.BaseSvc
import ru.neoflex.meta.utils.*
import ru.neoflex.meta.utils.vcs.IVCS
import MetaServer.rt.Environment
import MetaServer.utils.extensions.ExtensionRegistry

import java.text.SimpleDateFormat

/* protected region MetaServer.etlProject.inport on begin */
/* protected region MetaServer.etlProject.inport end */

class Project extends GenerationBase {
    /* protected region MetaServer.etlProject.statics on begin */
    private final static Log logger = LogFactory.getLog(Project.class)
    private static SimpleDateFormat jsonTimestampFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

    static Map findOrCreateProject(name = "autogenerated") {
        def db = Database.new
        def project = db.session.createQuery("from etl.Project where name = :name").setParameter("name", name).uniqueResult()
        if (project == null) {
            logger.info("create new project ${name}")
            project = db.instantiate("etl.Project", [name: name])
            db.save(project)
        }
        return project
    }

    static setProjectJsonView(project, boolean force) {
        def directDeps = MetaInfo.getAllDirectDeps(project)
        for (entity in directDeps) {
            setEntityJsonView(entity._type_, entity.e_id, force)
        }
    }

    static setEntityJsonView(entityType, e_id, boolean force) {
        def db = new Database("teneo")
        Map entity = db.get(entityType, (Long) e_id)
        setEntityJsonView(entity, force)
        db.update(entityType, entity)
    }

    static setEntityJsonView(Map entity, boolean force) {
        if (entity.containsKey("jsonView") && (entity.jsonView == null || entity.jsonView == "" || force)) {
            def emfModel = EMF.create("S", entity)
            def jsonView = Context.current.contextSvc.epsilonSvc.executeEgl("pim/etl/ui/psm/rapid/${entity._type_.split("\\.").last().toLowerCase()}JsonView.egl", [:], [emfModel])
            entity.jsonView = jsonView
        }
    }

    static List importFile(File xmiFile, List content, boolean emulate, boolean rename = false) {
        logger.info("Loading " + xmiFile.absolutePath)
        def imported = []
        def toImport = []
        def db = Database.new
        def resFrom = EMFResource.loadResource(xmiFile, new ResourceSetImpl())
        try {
            for (root in resFrom.contents) {
                def type = root.eClass().getEPackage().nsPrefix + "." + root.eClass().name
                def name = root.eGet(root.eClass().getEStructuralFeature("name"))
                logger.info("importing ${type} ${name}")
                def obj = [_type_: type, name: name]
                content.add(obj)
                def list = db.list(type, [name: name])
                if (list.size() > 0) {
                    logger.info("${type} ${name} already exists, merge...")
                    if (!emulate) {
                        def base = list.get(0)
                        def entity = ECoreUtils.prefetchEntity(base)
                        ECoreUtils.merge(EObjectMap.wrap(entity), EObjectMap.wrap(root), true)
                        Context.current.savepoint()
                    }
                } else {
                    if (!emulate) {
                        ECoreUtils.merge(null, EObjectMap.wrap(root), true)
                        Context.current.savepoint()
                    }
                }
                imported.add(obj)
            }
            if (!emulate) {
                if (rename) {
                    String newName = xmiFile.name + ".bak"
                    logger.debug("rename ${xmiFile.name} to ${newName}")
                    xmiFile.renameTo(new File(xmiFile.getParentFile(), newName))
                }
            }
        }
        catch (Throwable all) {
            logger.error("Invalid data! Import skipped: ${xmiFile.name}", all)
            Context.current.rollbackResources()
        }
        finally {
            resFrom.unload()
        }
        return imported
    }

    static List importProjectEntity(Map project, Map entity) {
        def projectDir = Project.getProjectDir(project)
        def xmiFile = Project.getFile(entity, projectDir, "xmi")
        def groovyFile = Project.getFile(entity, projectDir, "groovy")
        if (project.svnEnabled == true) {
            IVCS vcs = getVCS(project)
            vcs.update()
        }
        if (!xmiFile.file) {
            throw new RuntimeException("file ${xmiFile.name} not found")
        }
        def imported = Project.importFile(xmiFile, [], false)
        for (e in imported) {
            Project.postImportEntity(e, projectDir, false)
        }
        return imported
    }

    private static IVCS getVCS(Map project) {
        def vcsType = project.vcsType ?: "SVN"
        def userName, password
        if (project.connectAsLoggedInUser == true) {
            Context.User user = Context.current.user
            userName = user.getName()
            password = user.getPassword()
        } else {
            userName = project.svnUserName
            password = Project.getPassword(project)
        }

        def vcs = Context.current.contextSvc.vcsSvc.getVCS(
                vcsType.toString(),
                getProjectDir(project),
                userName,
                password,
                project.svnURL
        )
        vcs
    }

    static List exportProjectEntityWithDependentObjects(Map project, Map entity, svnCommitMessage) {
        def eCoreHelper = new ECoreHelper()
        def files = []
        eCoreHelper.getAllDependentObjectsOfEntity(entity).findAll {
            def e = Database.new.get(it)
            e.project?.e_id == project.e_id
        }.each {
            List newFiles = exportProjectEntity(project, it, svnCommitMessage)
            files.addAll(newFiles)
        }
        return files
    }

    static List exportProjectEntity(Map project, Map entity, svnCommitMessage) {
        def projectDir = Project.getProjectDir(project)
        def newFiles = Project.exportEntity(entity, projectDir, [].toSet())
        if (project.svnEnabled == true) {
            getVCS(project).commit(newFiles, SecurityContextHolder.context.authentication.name + ": " + svnCommitMessage)
        }
        logger.info("\n" + newFiles.join("\n"))
        return newFiles
    }

    static importDir(Database db, File dir, boolean scriptOnly, boolean rename = false) {
        def imported = []
        def content = []
        imported = importXmiFromDir(dir, imported, content, scriptOnly, rename)
        def scriptObjs = scriptOnly ? content : imported
        for (obj in scriptObjs) {
            postImportEntity(obj, dir, rename)
        }
        return imported
    }

    static void postImportEntity(Map obj, File dir, boolean rename) {
        def file = getFile(obj, dir, "groovy")
        if (file.exists()) {
            try {
                logger.info("post import ${file.name}")
                Eval.me(file.getText("UTF-8"))
                if (rename) {
                    String newName = file.name + ".bak"
                    logger.info("rename ${file.name} to ${newName}")
                    file.renameTo(new File(dir, newName))
                }
            } catch (Throwable all) {
                logger.error("Incorrect data! Import skipped: " + file.getName(), all)
            }
        }
    }

    static List importXmiFromDir(File dir, List imported, List content, emulate, rename = false) {
        for (file in dir.listFiles().findAll { it.getName().toLowerCase().endsWith(".xmi") }) {
            try {
                imported.addAll(importFile(file, content, emulate, rename))
            } catch (Throwable all) {
                logger.error("Incorrect data! Import skipped: " + file.getName(), all)
            }
        }
        return imported
    }

    static File getFile(Map entity, File projectDir, String ext) {
        return new File(projectDir, "${entity._type_.replace(".", "_")}_${entity.name}.${ext}")
    }

    static List<File> exportEntity(Map entity, File projectDir, Set<String> done) {
        def result = []
        def helper = new ECoreHelper()
        entity = helper.getObject(entity._type_, entity.e_id, entity.name)
        if (done.contains(entity.hash)) {
            return result
        }
        done.add(entity.hash)
        logger.info("Exporting ${entity._type_} ${entity.name} into ${projectDir.getAbsolutePath()}")
        def file = getFile(entity, projectDir, "xmi")
        exportEntities([entity], file)
        result.add(file)
        //entity.refs = []
        //MetaInfo.getAllExternalRefs(MetaInfo.getClassInfos(), entity, entity.refs)
        entity.links = helper.getGraphLinks(entity)
        if (entity.links.size() > 0) {
            def scriptPatt = "psm/etl/groovy/post_import2.egl"
            def scriptURI = MetaResource.getURISafe(scriptPatt)
            if (scriptURI != null) {
                def script = Context.current.contextSvc.epsilonSvc.executeEgl(scriptURI, MetaResource.parentDirPath(scriptPatt), [entity: entity], [])
                def piFile = getFile(entity, projectDir, "groovy")
                logger.info("Post import script: ${piFile.name}")
                piFile.withWriter("UTF-8") { it << script }
                result.add(piFile)
            }
        }
        helper.getAllDependentObjects(entity).each {
            result.addAll(exportEntity(it, projectDir, done))
        }
        def extension = ExtensionRegistry.instance.get(entity)
        if (extension != null) {
            result.addAll(extension.export(new File(projectDir, "_src")))
        }
        return result
    }

    static void exportEntities(List objs, File xmiFile) {
        def db = new Database("teneo")
        def resTo = EMFResource.createResource(xmiFile, new ResourceSetImpl())
        try {
            resTo.getContents().addAll(EcoreUtil.copyAll(objs.collect { db.get(it._type_, (Long) it.e_id) }))
            logger.info("saving ${xmiFile.name}")
            resTo.save([PROCESS_DANGLING_HREF: "DISCARD"])
        }
        finally {
            resTo.unload()
        }
    }

    static String getPassword(Map entity) {
        return Common.getDecryptedPassword("etl.Project.${entity.name}.svnPassword", entity.svnPassword as String)
    }

    private static void initRepo(File initDir, boolean rename = false) {
        if (initDir.exists() && initDir.list().any { it.toLowerCase().endsWith(".xmi") }) {
            def query = '''SELECT 'TRUNCATE TABLE '
                               || array_to_string(array_agg(quote_ident(schemaname) || '.' || quote_ident(tablename)), ', ')
                               || ' CASCADE'
                           FROM   pg_tables
                           WHERE  schemaname = (select current_schema());'''
            def truncate = Context.current.session.createSQLQuery(query).list().get(0)
            Context.current.txSession.createSQLQuery(truncate).executeUpdate()
            Context.current.savepoint()
            Project.importDir(Database.new, initDir, false, rename)
            Context.current.savepoint()
            Environment.rewriteCurrent(null)
        }
    }

    /* protected region MetaServer.etlProject.statics end */

    static Object importProject(Map entity, Map params = null) {
        /* protected region MetaServer.etlProject.importProject on begin */
        def db = new Database("teneo")
        def project = db.get("etl.Project", (Long) entity.e_id)
        logger.info("Import project ${project._type_}[${project.name}]")
        def projectDir = getProjectDir(project)
        if (project.svnEnabled == true) {
            svnCheckoutOrUpdate(entity, null)
        }
        MClass.deduplicateTopLevels()
        return importDir(db, projectDir, false)
        /* protected region MetaServer.etlProject.importProject end */
    }

    static File getProjectDir(Map project) {
        return new File(BaseSvc.getDeployDir(), "cim/MetaServer/data/projects/${project.name}")
    }

    static Object exportProject(Map entity, Map params = null) {
        /* protected region MetaServer.etlProject.exportProject on begin */
        def db = new Database("teneo")
        def project = db.get("etl.Project", (Long) entity.e_id)
        logger.info("Export project ${project._type_} ${project.name}")
        def projectDir = getProjectDir(project)
        projectDir.mkdirs()
        logger.info("Export dir: ${projectDir.canonicalPath}")
        FileSystem.clearFolder(projectDir)
        def done = [].toSet()
        //def ents = MetaInfo.getAllRefsOfAllDirectDepsWithRefs(entity).findAll {MetaInfo.isTopLevelEntity(it)}
        def ents = (new ECoreHelper()).getAllReferencedObjectsOfDependedObjects(project)
        for (ent in ents) {
            exportEntity(ent, projectDir, done)
        }
        if (project.svnEnabled == true) {
            project.svnCommitMessage = entity.svnCommitMessage
            db.save(project)
            svnCommit(entity, null)
        }
        return ents.collect { [_type_: it._type_, name: it.name, e_id: it.e_id] }
        /* protected region MetaServer.etlProject.exportProject end */
    }

    static Object importRepo(Map entity, Map params = null) {
        /* protected region MetaServer.etlProject.importRepo on begin */
        def initDir = new File(BaseSvc.getDeployDir(), "cim/MetaServer/data/all")
        initRepo(initDir)
        /* protected region MetaServer.etlProject.importRepo end */
    }

    static Object exportRepo(Map entity, Map params = null) {
        /* protected region MetaServer.etlProject.exportRepo on begin */
        def entities = MetaInfo.getTopLevelEntities(false).sort { "${it._type_} ${it.name}" }
        logger.info("Exporting repository. Objects count: ${entities.size()}")
        def dir = new File(BaseSvc.getDeployDir(), "cim/MetaServer/data/all")
        FileSystem.clearFolder(dir)
        def done = [].toSet()
        for (ent in entities) {
            try {
                exportEntity(ent, dir, done)
            }
            catch (Throwable th) {
                logger.error("${ent._type_}[${ent.e_id}/${ent?.name}]", th)
            }
        }
        return entities.collect { [_type_: it._type_, name: it.name, e_id: it.e_id] }
        /* protected region MetaServer.etlProject.exportRepo end */
    }

    static Object clear(Map entity, Map params = null) {
        /* protected region MetaServer.etlProject.clear on begin */
        def db = new Database("teneo")
        def directDeps = MetaInfo.getAllDirectDeps(entity)
        def deps = MetaInfo.getAllDeps(entity).findAll { 'dwh.StagingArea' != it._type_ && 'rt.ImportWizard' != it._type_ }
        for (dep in deps) {
            def depString = "Dependency ${dep._type_}[${dep.e_id}/${dep.name}]".toString()
            logger.debug(depString)
            if (!directDeps.any { it._type_ == dep._type_ && it.e_id == dep.e_id }) {
                throw new RuntimeException(depString + " not in project")
            }
            db.delete(dep._type_, db.get(dep._type_, (Long) dep.e_id))
        }
        return deps.collect { [_type_: it._type_, name: it.name, e_id: it.e_id] }
        /* protected region MetaServer.etlProject.clear end */
    }

    static Object setJsonView(Map entity, Map params = null) {
        /* protected region MetaServer.etlProject.setJsonView on begin */
        if (entity == null) {
            return ["null entity"]
        }
        setProjectJsonView(entity, false)
        return ["Hello from MetaServer.etl.Project.setJsonView"]
        /* protected region MetaServer.etlProject.setJsonView end */
    }

    static Object importScripts(Map entity, Map params = null) {
        /* protected region MetaServer.etlProject.importScripts on begin */
        def db = new Database("teneo")
        def project = db.get("etl.Project", (Long) entity.e_id)
        logger.info("Import scripts for ${project._type_}[${project.name}]")
        def projectDir = getProjectDir(entity)
        return importDir(db, projectDir, true)
        /* protected region MetaServer.etlProject.importScripts end */
    }

    static Object clearLost(Map entity, Map params = null) {
        /* protected region MetaServer.etlProject.clearLost on begin */
        def restored = []
        def deleted = []
        MetaInfo.getLost(deleted, restored)
        return deleted
        /* protected region MetaServer.etlProject.clearLost end */
    }

    static Object svnCheckout(Map entity, Map params = null) {
        /* protected region MetaServer.etlProject.svnCheckout on begin */
        if (entity.svnEnabled != true) {
            return ["VCS not enabled for project ${entity.name}".toString()]
        }
        getVCS(entity).checkout()
        return ["OK"]
        /* protected region MetaServer.etlProject.svnCheckout end */
    }

    static Object svnUpdate(Map entity, Map params = null) {
        /* protected region MetaServer.etlProject.svnUpdate on begin */
        if (entity.svnEnabled != true) {
            return ["VCS not enabled for project ${entity.name}".toString()]
        }
        getVCS(entity).update()
        return ["OK"]
        /* protected region MetaServer.etlProject.svnUpdate end */
    }

    static Object svnCheckoutOrUpdate(Map entity, Map params = null) {
        /* protected region MetaServer.etlProject.svnCheckoutOrUpdate on begin */
        if (entity.svnEnabled != true) {
            return ["VCS not enabled for project ${entity.name}".toString()]
        }
        def vcs = getVCS(entity)
        if (vcs.isVersioned()) {
            vcs.update()
        } else {
            vcs.checkout()
        }
        return ["OK"]
        /* protected region MetaServer.etlProject.svnCheckoutOrUpdate end */
    }

    static Object svnCommit(Map entity, Map params = null) {
        /* protected region MetaServer.etlProject.svnCommit on begin */
        if (entity.svnEnabled != true) {
            return ["VCS not enabled for project ${entity.name}".toString()]
        }
        def projectDir = getProjectDir(entity)
        getVCS(entity).commit(SecurityContextHolder.context.authentication.name + ": " + entity.svnCommitMessage)
        return ["OK"]
        /* protected region MetaServer.etlProject.svnCommit end */
    }

    static Object svnCleanup(Map entity, Map params = null) {
        /* protected region MetaServer.etlProject.svnCleanup on begin */
        if (entity.svnEnabled != true) {
            return ["VCS not enabled for project ${entity.name}".toString()]
        }
        getVCS(entity).cleanup()
        return ["OK"]
        /* protected region MetaServer.etlProject.svnCleanup end */
    }

    static Object downloadArchive(Map entity, Map params = null) {
        if (params.export && Boolean.parseBoolean(params?.export ?: "false")) {
            exportProject(entity, params)
        }
        File projectDir = getProjectDir(entity)
        PipedInputStream pipedInputStream = new PipedInputStream()
        PipedOutputStream pipedOutputStream = new PipedOutputStream(pipedInputStream)
        new Thread() {
            void run() {
                ZipUtils.zipDirectory(projectDir, pipedOutputStream, true, "^\\..*")
            }
        }.start()
        HttpHeaders headers = new HttpHeaders()
        headers.set("Content-Type", "application/zip")
        headers.set("Content-Disposition", "attachment; filename=\"${entity.name}.zip\"")
        return new ResponseEntity(new InputStreamResource(pipedInputStream), headers, HttpStatus.OK)
    }

    static Object uploadArchive(Map entity, Map params = null) {
        def file = params.file
        if (file == null) {
            return ["Zip file not found"]
        }
        File projectDir = getProjectDir(entity)
        projectDir.mkdirs()
        logger.info("Upload dir: ${projectDir.canonicalPath}")
        FileSystem.clearFolder(projectDir)
        def count = ZipUtils.unzipToDirectory(projectDir, file.inputStream as InputStream)
        def imported = importDir(Database.new, projectDir, false)
        return ["${count} file(s) unzipped. ${imported.size()} object(s) imported.".toString()]
    }

    static Object svnProps(Map project, Map entity) {
        if (project != null && project.svnEnabled) {
            def projectDir = Project.getProjectDir(project)
            def file = Project.getFile(entity, projectDir, "xmi")
            def fileInfo = getVCS(project).getFileInfo(file)
            if (fileInfo != null) {
                return [
                        lastCommitAuthor   : fileInfo.lastCommitAuthor,
                        lastChangedDate    : fileInfo.lastChangedDate,
                        lastChangedRevision: fileInfo.lastChangedRevision,
                        logMessage         : fileInfo.logMessage
                ]
            }
        }
        return [:]
    }

    static Object downloadData(Map entity, Map params = null) {
        def path = params?.path
        if (StringUtils.isBlank(path)) {
            throw new RuntimeException("Dowload path is empty")
        }
        def dataDir = new File(getProjectDir(entity), "_data")
        if (!dataDir.isDirectory()) {
            return [result: "No data directory found: " + dataDir.absolutePath]
        }
        def livy = LivyServer.getDefaultLivyServer()
        logger.info("Download ${path} to ${dataDir.absolutePath} with default Livy ${livy.name}".toString())
        def list = LivyServer.downloadDirectory(livy, path, dataDir)
        return [result: "OK", list: list]
    }

    static Object uploadData(Map entity, Map params = null) {
        def dataDir = new File(getProjectDir(entity), "_data")
        if (!dataDir.isDirectory()) {
            return [result: "No data directory found: " + dataDir.absolutePath]
        }
        def livy = LivyServer.getDefaultLivyServer()
        logger.info("Upload ${dataDir.absolutePath} with default Livy ${livy.name}".toString())
        def list = LivyServer.uploadDirectory(livy, dataDir, "/")
        return [result: "OK", list: list]
    }
}
