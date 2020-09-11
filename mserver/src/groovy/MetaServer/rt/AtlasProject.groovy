package MetaServer.rt

import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import MetaServer.utils.AtlasEntity
import groovy.json.*
import groovyx.net.http.ContentType
import groovyx.net.http.RESTClient
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.Context
import ru.neoflex.meta.utils.ECoreUtils
/* protected region MetaServer.rtAtlasScheme.inport end */

class AtlasProject {
    private final static Log logger = LogFactory.getLog(AtlasProject.class)
    /* protected region MetaServer.rtAtlasScheme.statics on begin */
    def static partSize = 100

    private static def evalStep(step, propertyName, scalaSvc, params) {
        def s = step[propertyName]
        def result = [result: true]
        def codePattern = "val jobParameters = Map("
        for(p in params){
            codePattern = codePattern + "\"" + p.name + "\" -> \"" + p.value + "\""
            if(p != params.last()) {
                codePattern = codePattern + ", "
            }
        }
        codePattern = codePattern + ") \n" + 's"""' + s + '"""'
        scalaSvc.eval(codePattern, [], result)
        step[propertyName] = result.value
    }
    
    private static def Map cloneAndevalObject(object, scalaSvc, params) {
        def om = ECoreUtils.getMap(object)
        evalIt(om, scalaSvc, params)
        om
    }
    
    private static def evalIt(step, scalaSvc, params) {
        if(step.contextFromString == true) {
            evalStep(step, "contextString", scalaSvc, params)
        }
        if(step._type_ == "etl.TableSource") {
            evalStep(step, "tableName", scalaSvc, params)
        }

        if(step._type_ == "etl.SQLSource") {
            if(step.rdbmsSources != null){
                step.rdbmsSources.eachWithIndex { item, index ->
                    def fakestep = ["rdbmsSource": item] 
                    evalStep(fakestep, "rdbmsSource", scalaSvc, params)
                    step.rdbmsSources[index] = fakestep.rdbmsSource 
                }
            }
            evalStep(step, "statement", scalaSvc, params)
        }
        if (AtlasEntity.isFileEntity(step._type_) || step._type_ == "etl.LocalTarget" ||
                step._type_ == "etl.CSVTarget" || step._type_ == "etl.XMLTarget") {
            evalStep(step, (step._type_ == "etl.LocalTarget" || step._type_ == "etl.LocalSource") ? "localFileName" : "path", scalaSvc, params)
        }
    }
    
    public static Object publish(Map entity, Map params = null) {
        
        def db = Database.new
        def atlasProject = db.get(entity)
        def atlas = atlasProject.atlas
        
        def workflowDeplymentsList = db.session.createQuery("select d from rt.WorkflowDeployment d where d.start.project.e_id=${atlasProject.project.e_id}").list()
        def contextDeployments = [:]
        def importTransformation = new ImportTransformation()
        def toCreate = [:]
        toCreate.entities = [:]
        toCreate.inputs = [:]
        toCreate.outputs = [:]
        for(wd in workflowDeplymentsList) {            
            def transformationNodes = db.session.createQuery("select n from etl.WFTransformation n left join fetch n.parameters p where n.workflow.e_id=${wd.start.e_id}").list()
            for(tn in transformationNodes) {
                def transformation = tn.transformation
                def parameters = []
                parameters += wd.parameters
                parameters += tn.parameters
                importTransformation.importTransformation(transformation, parameters, atlas, db, toCreate)
            }
        }
        
        def listToCreate = toCreate.entities.collect{ it.value }
        def createdEntites = AtlasScheme.importData(atlas, listToCreate)
        createdEntites = createdEntites[0]
        def mutated = createdEntites.mutatedEntities
        
        for(entry in toCreate.inputs) {
            for(i in entry.value) {
                def qn = i.qualifiedName
                def created = mutated.UPDATE.find{it-> it.attributes.qualifiedName == qn}
                if(created == null) {
                    created = mutated.CREATE.find{it-> it.attributes.qualifiedName == qn}
                }
                if(created == null) {
                    created = importTransformation.findDescription(qn)
                }
                i.guid = created.guid
            } 
        }

        for(entry in toCreate.outputs) {
            for(i in entry.value) {
                def qn = i.qualifiedName
                def created = mutated.UPDATE.find{it-> it.attributes.qualifiedName == qn}
                if(created == null) {
                    created = mutated.CREATE.find{it-> it.attributes.qualifiedName == qn}
                }
                i.guid = created.guid
            }
        }
        
        List processes = []
        processes += mutated.UPDATE?:[].findAll{it -> it.typeName == "Process"}
        processes += mutated.CREATE?:[].findAll{it -> it.typeName == "Process"}
        
        for(process in processes) {
            if(process.typeName == "Process") {
                def processQName = process.attributes.qualifiedName
                def inputs = toCreate.inputs[processQName].collect{it -> [guid: it.guid, typeName: it.typeName]}
                def outputs = toCreate.outputs[processQName].collect{it -> [guid: it.guid, typeName: it.typeName]}
                def processEntity = toCreate.entities[processQName]
                
                def atlasProcess = AtlasScheme.getByGuid(atlas, process.guid)
                
                atlasProcess.entity.relationshipAttributes = null
                
                atlasProcess.entity.attributes.inputs = inputs
                atlasProcess.entity.attributes.outputs = outputs
                
                AtlasScheme.updateEntity(atlas, atlasProcess)
            }
        }
        
        logger.info("Finished!")
        return [result: "Finished!"]
    }    
    
    private static deleteProcess(atlas, process) {
        def guids = AtlasScheme.getDeletedGuids(atlas, process.getQueryLikeValue(), [], AtlasEntity.TypeName.Process)
        def guidsToDelete = []
        guids.each {
            def atlasProcess = AtlasScheme.getByGuid(atlas, it)
            guidsToDelete = atlasProcess?.entity?.attributes.inputs ?: [].plus(atlasProcess?.entity?.attributes.outputs ?: []).collect {
                it.guid
            }
        }
        
        AtlasScheme.bulkDeleteEntities(atlas, guidsToDelete)
        AtlasScheme.bulkDeleteEntities(atlas, AtlasScheme.getDeletedGuids(atlas, process.getQueryLikeValue(), [], AtlasEntity.TypeName.Process))
    }              
    /* protected region MetaServer.rtAtlasScheme.statics end */
}

class ImportTransformation {
    private final Log logger = LogFactory.getLog(ImportTransformation.class)
    def contextDeployments = [:]
    def atlasObjects = [:]
    
    public Object findDescription(String qualifiedName) {
        for(entry in atlasObjects) {
            for(val in entry.value) {
                if(val.attributes.qualifiedName == qualifiedName) {
                    return val;
                }
            }
        }
        return null;
    }
    
    public importTransformation(transformation, parameters, atlas, db, toCreate) {
        logger.info("Generate lineage for: " + transformation.name)
        def AtlasEntity sparkProcess = AtlasEntity.createProcessEntity(transformation)
        def inputs = []
        def outputs = []
        
        def sourcesAndTargets = [:]
        def scalaSvc = Context.current.contextSvc.scalaSvc
        for(source in transformation.sources) {
            def step = AtlasProject.cloneAndevalObject(source, scalaSvc, parameters)

            if(AtlasEntity.isFileEntity(step._type_)) {
                def AtlasEntity fileEntity = AtlasEntity.createLocalFileEntity(step, transformation, atlas)
                sourcesAndTargets.put(fileEntity.ggetQualifiedName(), fileEntity)
                inputs += ["qualifiedName": fileEntity.ggetQualifiedName(), "typeName": fileEntity.typeName]
            }
            if(step._type_ == "etl.SQLSource" || step._type_ == "etl.TableSource") {
                                    
                def rdbmsSources = []
                def contextName = step.contextFromString == true ? step.contextString : step.context?.name
                if(step._type_ == "etl.SQLSource"){
                    rdbmsSources = step.rdbmsSources
                } else {
                    rdbmsSources.add(step.tableName)
                }
                if(((java.lang.String)rdbmsSources).trim().length() != 0 && contextName != null) {
                    def deployment = contextDeployments[contextName]
                    def connectionData = AtlasScheme.initConnectionData("public")
                    if(deployment == null) {
                        def deploymentsList = db.session.createQuery("from rt.Deployment where project.e_id=${atlas.project.e_id} and softwareSystem.name = '${contextName}'").list()
                        if(deploymentsList.size() == 0) {
                            deploymentsList = db.session.createQuery("from rt.Deployment where softwareSystem.name = '${contextName}'").list()
                        }
                        
                        if(deploymentsList.size() == 1) {
                            def d = deploymentsList.get(0)
                            contextDeployments[contextName] = d
                            AtlasScheme.buildConnectionData(connectionData, d)
                            try {
                                this.atlasObjects[contextName] = AtlasScheme.searchByTypeName(atlas, AtlasEntity.prepareQualifiedName(connectionData, ["*"]), AtlasEntity.TypeName.rdbms_table, 0, "LIKE")
                            } catch(e) {
                               logger.log(e)
                            }
                        }
                    } else {
                        AtlasScheme.buildConnectionData(connectionData, deployment)
                    }

                    for(s in rdbmsSources) {
                        def qualifiedName = s
                        def rdbmsSourceList = [this.findDescription(qualifiedName)].findAll{it != null}
                        if(rdbmsSourceList.size() == 0) {
                            qualifiedName = AtlasEntity.prepareQualifiedName(connectionData, [qualifiedName])
                            rdbmsSourceList = [this.findDescription(qualifiedName)].findAll{it != null}
                        }
                        
                        for(rdbmsSourcein in rdbmsSourceList) {
                            inputs += ["qualifiedName": qualifiedName, "typeName": AtlasEntity.TypeName.rdbms_table]
                        }
                    }
                } else {
                    def AtlasEntity sqlSourceEntity = AtlasEntity.createTableEntity([host:"sql", port: "sql", databaseProductName: "sql"], step, false)
                    sqlSourceEntity.attributes.description = step.statement
                    sourcesAndTargets.put(sqlSourceEntity.ggetQualifiedName(), sqlSourceEntity)
                    inputs += ["qualifiedName": sqlSourceEntity.ggetQualifiedName(), "typeName": sqlSourceEntity.typeName]
                }
            }
        }
        for(target in transformation.targets) {
            def step = AtlasProject.cloneAndevalObject(target, scalaSvc, parameters)
            if(step._type_ == "etl.LocalTarget") {
                def AtlasEntity fileEntity = AtlasEntity.createLocalFileEntity(step, transformation, atlas)
                sourcesAndTargets.put(fileEntity.ggetQualifiedName(), fileEntity)
                outputs += ["qualifiedName": fileEntity.ggetQualifiedName(), "typeName": fileEntity.typeName]
            }
        }

        //AtlasProject.deleteProcess(atlas, sparkProcess)
        
        toCreate.entities.put(sparkProcess.ggetQualifiedName(),sparkProcess)
        def sandT= sourcesAndTargets.collect{it.value}
        toCreate.entities << sourcesAndTargets
        if(toCreate.inputs[sparkProcess.ggetQualifiedName()] == null) {
            toCreate.inputs[sparkProcess.ggetQualifiedName()] =[]
        }
        if(toCreate.outputs[sparkProcess.ggetQualifiedName()] == null) {
            toCreate.outputs[sparkProcess.ggetQualifiedName()] =[]
        }
        toCreate.inputs[sparkProcess.ggetQualifiedName()] += inputs
        toCreate.outputs[sparkProcess.ggetQualifiedName()] += outputs

    }
}
