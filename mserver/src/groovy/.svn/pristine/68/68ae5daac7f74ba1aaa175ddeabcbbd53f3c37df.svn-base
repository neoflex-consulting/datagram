package MetaServer.rt

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import java.sql.Connection
import java.util.Map

/* protected region MetaServer.rtLivyServer.inport on begin */
import ru.neoflex.meta.model.Database
import MetaServer.utils.EMF;
import MetaServer.rt.TransformationDeployment;
import ru.neoflex.meta.utils.Context;
import MetaServer.utils.GenerationBase
import java.nio.file.Files
import java.nio.file.Paths

/* protected region MetaServer.rtAtlasScheme.inport end */

class RunTask {
    
    private final static Log logger = LogFactory.getLog(Atlas.class)
    
    def static collectTasks(task, value){
        value.put(task.name, TransformationDeployment.getBatchParams(task.get("transformationDeployment"), task.get("transformationDeployment").get("transformation"), [:]));
        for(t in task.dependsOn) {
            //value.put(t.name, TransformationDeployment.getBatchParams(t.get("transformationDeployment"), t.get("transformationDeployment").get("transformation"), [:]));
            collectTasks(t, value);
        }
    }
    
    def public static generate(Map entity, Map params = null){
        
        def emfModel = EMF.create("src", entity)
        def db = Database.new
        def runTask = db.get(entity)
        def paramsMap = [:]
        collectTasks(runTask, paramsMap);
        def sourcesDir = GenerationBase.getSourcesDirectoryPath("TransformationDeployment")
        def generationFolder = new File(sourcesDir + "/${runTask.transformationDeployment.name}/${runTask.transformationDeployment.transformation.name}")

        def fileContent = Context.current.contextSvc.epsilonSvc.executeEgl("psm/etl/spark/RunTask.egl", [entity: runTask, folder: generationFolder, sourcesDir: sourcesDir, tasksparams: paramsMap], [emfModel]);
                
        if(!generationFolder.exists()){
            generationFolder.mkdirs();
        }
        def configFile = new File(generationFolder, "${runTask.name}.py")
        if(!configFile.exists()){
            configFile.createNewFile()
        } else {
            configFile.delete()
        }
        configFile << fileContent

        return [result: "Finished!", fileName: configFile.getPath()]
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
    
    def public static deployAllTransformations(Map entity, Map params = null){
        
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
    
    def public static deployDagToAirflow(Map entity, Map params = null) {
        def db = Database.new
        def runTask = db.get(entity)
        def sourcesDir = GenerationBase.getSourcesDirectoryPath("TransformationDeployment")
        def generationFolder = new File(sourcesDir + "/${runTask.transformationDeployment.name}/${runTask.transformationDeployment.transformation.name}")
        def dagFileName = "${generationFolder}/${runTask.name}.py"
        def dagsFolder = runTask.get("airflow").get("dagsFolder")
        Files.copy(Paths.get(dagFileName), Paths.get(dagsFolder + "/" + "${runTask.name}.py"))
        return [result: true, message: "Ready!"]
    }

}