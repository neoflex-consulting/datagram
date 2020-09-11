package MetaServer.sse

import org.eclipse.emf.ecore.EObject
import MetaServer.utils.ECoreHelper

import ru.neoflex.meta.utils.Context

import java.util.Map

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.ECoreUtils

abstract class Workspace {
    private final static Log log = LogFactory.getLog(Workspace.class)

    public static Object exportWorkspace(Map entity, Map params = null) {
        def workspace = Database.new.get(entity)
        def project = workspace.project
        def svnCommitMessage = params.svnCommitMessage
        if (project != null) {
            MetaServer.etl.Project.exportProjectEntity(project, workspace, svnCommitMessage)
        }
        return exportProjectEntityWithDependentObjects(workspace, params, svnCommitMessage)
    }


    static List exportProjectEntityWithDependentObjects(Map entity, Map params, String svnCommitMessage) {
        def eCoreHelper = new ECoreHelper()
        def files = new ArrayList<File>()
        def filesDeps = eCoreHelper.getAllDependentObjectsOfEntity(entity)
        def gitFlow = Context.current.getContextSvc().getGitflowSvc()
        //def branchDir = gitFlow.getBranchDir(gitFlow.getCurrentBranch())
        for(Object e : filesDeps){
            //Map merged = ECoreUtils.merge(null, e);
            def ent= Database.new.get(e)
            List<File> res  = gitFlow.exportEObject((EObject)ent)
            files.addAll(res)
        }
        //gitFlow.commit(gitFlow.getCurrentBranch(), files, svnCommitMessage);
        gitFlow.commit(gitFlow.getCurrentBranch(), svnCommitMessage)
        return files
    }

    public static Object importWorkspace(Map entity, Map params = null) {
        def workspace = Database.new.get(entity)
        def project = workspace.project
        if (project != null && project.svnEnabled) {
            MetaServer.etl.Project.importProjectEntity(project, workspace)
        }else{
            return importWorkspaceInternal(entity, params)
        }
    }

    static Object importWorkspaceInternal(Map entity, Map params = null){

        def gitFlow = Context.current.getContextSvc().getGitflowSvc()
        def projectDir = gitFlow.getBranchDir(gitFlow.getCurrentBranch())
        def xmiFiles = new ArrayList<File>()
        def refFiles = new ArrayList<File>()
        projectDir.traverse(type: groovy.io.FileType.FILES) { it ->
            if(it.name.equalsIgnoreCase(entity.get("name") + ".xmi")){
                xmiFiles.add(it)
            }
        }
        projectDir.traverse(type: groovy.io.FileType.FILES) { it ->
            if (it.name.equalsIgnoreCase(entity.get("name") + ".ref")) {
                refFiles.add(it)
            }
        }
        def files4Update = new ArrayList();
        files4Update.addAll(xmiFiles);
        files4Update.addAll(refFiles);
        def paths = new ArrayList<String>()
        for(File f : files4Update){
            def replace = f.getAbsolutePath().replace(projectDir.getAbsolutePath(), "")
            replace = replace.replace("\\", "/");
            replace = replace.substring(1, replace.length())
            paths.add(replace);
        }

        Context.current.getContextSvc().inContext(new Runnable() {
            @Override
            void run() {
                def updateFiles = Context.current.getContextSvc().getGitflowSvc().updatePaths(paths)
                for(File f : xmiFiles){
                    def imported = gitFlow.importXmiFile(f)
                }
                for(File f : refFiles){
                    def imported = gitFlow.importRefFile(f)
                }
            }
        });

        return [result: true, problems: false]
    }

    static Map getLivyServer(Map workspace) {
        def db = Database.new
        def ws = getWorkspace(workspace, db)

        def livy = ws.cluster?.livyServer
        if (livy == null) {
            livy = db.get(ws.cluster).livyServer
        }

        return livy
    }

    static Map getReferenceConnection(Map workspace) {
        def db = Database.new
        def ws = getWorkspace(workspace, db)

        def referenceConnection = ws.cluster?.referenceConnection
        if (referenceConnection == null) {
            referenceConnection = db.get(ws.cluster).referenceConnection
        }
        if (referenceConnection == null ||
                referenceConnection.driver == null ||
                !(referenceConnection.driver as String).contains("PhoenixDriver")) {
            throw new RuntimeException("No Phoenix jdbc connection found")
        }

        return referenceConnection
    }

    static Map getWorkspace(Map workspace, Database db = null) {
        if (db == null) {
            db = Database.new
        }
        def ws = db.get(workspace)
        if (ws.cluster == null) {
            throw new RuntimeException("Cluster not defined for workspace " + ws.name)
        }
        ws
    }

    static Map getHiveConnection(Map workspace) {
        def db = Database.new
        def ws = getWorkspace(workspace, db)

        def hive = ws.cluster?.hiveConnection
        if (hive == null) {
            hive = db.get(ws.cluster).hiveConnection
        }

        return hive
    }

    static String getHiveMetastoreUri(Map workspace) {
        def db = Database.new
        def ws = getWorkspace(workspace, db)

        def hiveM = ws.cluster?.hiveMetastoreUri
        if (hiveM == null) {
            hiveM = db.get(ws.cluster).hiveMetastoreUri
        }

        return hiveM
    }

    static Map<String, String> getWorkspaceParameters(Map workspace) {
        def db = Database.new
        def ws = db.get(workspace)

        def params = ws.parameters ?: []
        def jobParams = [:]
        params.forEach {p -> jobParams.put(p.name, p.value) }

        return jobParams
    }
    
    public static Map fullCopy(Map entity, Map params = null, String nameRoot, String targetClass, Boolean parentLink) {
        def db = Database.new
        def toCopy = db.get(entity)
        def i = 1
        def name = nameRoot?:"copy"
        while(true) {
            if(db.select("from " + toCopy._type_ + " where name = " + "'" + name + "'", null).size == 0) {
                break;
            } else {
                i++
                name = nameRoot ? nameRoot + "_" + i : "copy_" + i
            }
        }
        
        def Map copy = ECoreUtils.copyEntityToDerivedClass(toCopy, name, targetClass?:toCopy._type_)
        copy.shortName = name
        if(parentLink == true) {
            copy.parent = toCopy
        }        
        List nodes = db.select("select a from sse.AbstractNode a where a.workspace.e_id = " + toCopy.e_id, [:])
        List nodes2 = []
        Map oldNew = [:]
        for(node in nodes){
            def String nodeName = node.name
            if(nodeName.contains(toCopy.name)) {
                nodeName = nodeName.replace(toCopy.name, copy.name)
            } else {
                nodeName = copy.name + "_" + nodeName
            }
            
            def node2 = ECoreUtils.copyEntity(node, nodeName)
            node2.workspace = copy
            db.save(node2)
            nodes2 += node2
            oldNew.put(node, node2)
        }
        
        for(node in nodes2) {
            List datasets = []
            for(dataset in node.datasets) {
                if(dataset.workspace.e_id == toCopy.e_id) {
                    datasets += oldNew.get(nodes.find{d -> d.e_id == dataset.e_id})
                } else {
                    datasets += dataset
                }
            }
            node.datasets = datasets
            db.save(node)
        }
        
        db.save(copy)
        
        return [status: "OK", problems: []]
    }
}
