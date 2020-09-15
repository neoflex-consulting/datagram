package MetaServer.rt

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import java.sql.Connection
import java.util.Map

/* protected region MetaServer.rtLivyServer.inport on begin */
import ru.neoflex.meta.model.Database
import MetaServer.utils.EMF
import MetaServer.rt.TransformationDeployment
import ru.neoflex.meta.utils.Context
import MetaServer.utils.GenerationBase
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Callable

/* protected region MetaServer.rtAtlasScheme.inport end */

class RunTask {
    
    private final static Log logger = LogFactory.getLog(Atlas.class)
    
    def static collectTasks(task, value){
        value.put(task.name, TransformationDeployment.getBatchParams(task.get("transformationDeployment"), task.get("transformationDeployment").get("transformation"), [:]))
        for(t in task.dependsOn) {
            //value.put(t.name, TransformationDeployment.getBatchParams(t.get("transformationDeployment"), t.get("transformationDeployment").get("transformation"), [:]));
            collectTasks(t, value)
        }
    }
    
    def static generate(Map entity, Map params = null){
        
        def emfModel = EMF.create("src", entity)
        def db = Database.new
        def runTask = db.get(entity)
        def paramsMap = [:]
        collectTasks(runTask, paramsMap)
        def fileContent = Context.current.contextSvc.epsilonSvc.executeEgl("psm/etl/spark/RunTask.egl", [entity: runTask, tasksparams: paramsMap], [emfModel])

        def gitFlow = Context.current.getContextSvc().getGitflowSvc()
        return gitFlow.inGitTransaction("genetate ${runTask.name}.py", new Callable<Map>() {
            @Override
            Map call() throws Exception {
                def genPath = gitFlow.getCurrentGfs().getRootPath().resolve(gitFlow.SOURCES + "/TransformationDeployment/${runTask.transformationDeployment.name}/${runTask.transformationDeployment.transformation.name}/${runTask.name}.py")
                Files.createDirectories(genPath.getParent())
                Files.write(genPath, fileContent.bytes)
                return [result: "Finished!", fileName: configFile.getPath()]
            }
        })
    }
    
    private static def getDeps(ent, list) {
        list.add(ent.transformationDeployment)
        for(e in ent.dependsOn) {
                list.add(e.transformationDeployment)
                getDeps(e, list)
            }
    }
    
    static Object chain(fs, Map entity, Map params = null) {
        def problems = []
        def data = params ?: [:]
        for (f in fs) {
            def ret = f(entity, data)
            problems += ret.problems
            data.putAll(ret.data ?: [:])
            if (!ret.result) return [result: false, problems: problems, data: data]
        }
        return [result: true, problems: problems, data: data]
    }
    
    def static deployAllTransformations(Map entity, Map params = null){
        
        def db = Database.new
        def runTask = db.get(entity)
        
        Set deployments = []
        
        getDeps(runTask, deployments)
        
        for(d in deployments) { 
            def result = chain([
                TransformationDeployment.&generate,
                TransformationDeployment.&build,
                TransformationDeployment.&deploy
            ], d)            
        }
                
        return [result: "Finished!"]
    }
    
    def static deployDagToAirflow(Map entity, Map params = null) {
        def db = Database.new
        def runTask = db.get(entity)
        def gitFlow = Context.current.getContextSvc().getGitflowSvc()
        return gitFlow.inGitTransaction(null, new Callable<Map>() {
            @Override
            Map call() throws Exception {
                def root = gitFlow.getCurrentGfs().getRootPath()
                def dagPath = root.resolve(gitFlow.SOURCES + "/TransformationDeployment/${runTask.transformationDeployment.name}/${runTask.transformationDeployment.transformation.name}/${runTask.name}.py")
                def dagsFolder = runTask.get("airflow").get("dagsFolder")
                Files.copy(dagPath, Paths.get(dagsFolder + "/" + "${runTask.name}.py"))
                return [result: true, message: "Ready!"]
            }
        })
    }

}