package MetaServer.rt

import MetaServer.etl.Transformation
import MetaServer.utils.EMF
import MetaServer.utils.FileSystem
import MetaServer.utils.GenerationBase
import MetaServer.utils.HDFSClient
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.eclipse.epsilon.common.util.StringProperties
import org.eclipse.epsilon.emc.emf.EmfModel
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.Context

/* protected region MetaServer.rtTransformationDeployment.inport on begin */
import ru.neoflex.meta.utils.JSONHelper
import ru.neoflex.meta.utils.SymmetricCipher

import java.nio.file.Paths

import static java.nio.file.Files.readAllBytes
import static java.nio.file.Files.write
import static java.nio.file.Paths.get

/* protected region MetaServer.rtTransformationDeployment.inport end */

class TransformationDeploymentCustom extends TransformationDeployment {
   
    static Object transformation2Hive(Map entity, Map params = null) {
        /* protected region MetaServer.etlTransformation.runit on begin */
          def result = []
          Context.current.commit()
          def outputType
          if (entity.outputType == null) outputType = 'text' else outputType = entity.outputType
          
          def deployDir = getSourcesDirectoryPath("TransformationDeployment")
          def trDeployment = new Database("teneo").get("rt.TransformationDeployment", (Long) entity.e_id)
          def transformation = trDeployment.transformation
          File file = new File(deployDir, "${trDeployment.name}/${transformation.name}2Hive.scala")
          file.delete()
          def emfModel = new EmfModel()
          def properties = new StringProperties()
          properties.put(EmfModel.PROPERTY_NAME, "src")
          properties.put(EmfModel.PROPERTY_MODEL_URI, "hibernate://?dsname=teneo&query1=from etl.Transformation where e_id=${transformation.e_id}")
          properties.put(EmfModel.PROPERTY_METAMODEL_URI, "http://www.neoflex.ru/meta/etl")
          properties.put(EmfModel.PROPERTY_READONLOAD, "true")
          emfModel.load(properties, "")
          def jobParams = ["HOME=${trDeployment.livyServer.home}".toString()]
  
          def deployments = []
          trDeployment.deployments.each {
              def deployment = [
                      NAME    : "${it.softwareSystem.name}".toString(),
                      URL     : "${it.connection.url}".toString(),
                      USER    : "${it.connection.user}".toString(),
                      DRIVER  : "${it.connection.driver}".toString(),
                      PASSWORD: "${JdbcConnection.getPassword(it.connection)}".toString(),
                      SCHEMA  : "${it.connection.schema}".toString()
              ]
              deployments.add(deployment)
          }
          trDeployment.parameters.each {
              jobParams.add(JSONHelper.escape("${it.name}=${it.value}").toString())
          }
  
          Context.current.getContextSvc().epsilonSvc.executeEgx("/psm/etl/spark/Transformation2Hive.egx", [mspaceRoot: "file:///" + deployDir, jobDeployment: trDeployment, nodeName: params.nodeName, outputType: params.outputType, jobParams: jobParams, deployments: deployments, sampleSize: params.sampleSize, statement: params.statement], [emfModel])
          return [result: true, fileContent: new String(readAllBytes(get(file.getPath())))]
          /* protected region MetaServer.rtTransformationDeployment.generatePart end */
    }
}
