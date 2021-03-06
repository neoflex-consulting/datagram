package MetaServer.utils

import java.util.Map
import ru.neoflex.meta.model.Database
import MetaServer.sse.JdbcDataset
import groovy.json.*
import groovy.text.SimpleTemplateEngine

class AtlasEntity {
        
    TypeName typeName    
    Map attributes
    String status = "ACTIVE"
    //String guid
    
    AtlasEntity(){

    }
    
    AtlasEntity(typeName, attributes) {
        this.typeName = typeName 
        this.attributes = attributes
      //  this.guid = UUID.randomUUID().toString()
    }
    
    public enum TypeName {rdbms_instance, rdbms_db, rdbms_table, rdbms_column, rdbms_index, rdbms_foreign_key, Process, fs_path, hdfs_path, hive_db, hive_table}
    
    def String ggetQualifiedName() {
        return this.attributes.qualifiedName
    }
    
    def String getQueryLikeValue() {
        def result
        switch(this.typeName) {
          case TypeName.rdbms_instance:
              result = this.ggetQualifiedName()
              break
          case TypeName.rdbms_db:
            def name = this.ggetQualifiedName()
            result = name.indexOf("@") == -1 ? name : "${name.substring(0, name.indexOf("@"))}.*${name.substring(name.indexOf("@"))}"
            break
          default:
            def name = this.ggetQualifiedName()
            result = name.indexOf("@") == -1 ? name : "${name.substring(0, name.indexOf(".") + 1)}*${name.substring(name.indexOf("@"))}"
            break
        }
        return result
      }
      
     
    def static AtlasEntity createColumnEntity(Map connectionData, Map column) {
      def tableName = column.dataSet.name
      def columnName = column.name
      def tableAttributes = [
        "table": AtlasReferredEntity.createReferredEntity(TypeName.rdbms_table, prepareQualifiedName(connectionData, [tableName])),
        "name": columnName,
        "qualifiedName": prepareQualifiedName(connectionData, [tableName, columnName]),
        "data_type": JdbcDataset.rel2DataType.get(column.dataType._type_.toString()),
        "length": column.length,
        //"default_value": column.getString("COLUMN_DEF"),
        "comment": column.description,
        "isNullable": column.nullable
      ]
      new AtlasEntity(TypeName.rdbms_column, tableAttributes)
    }
  
    def static AtlasEntity createIndexEntity(Map connectionData, Map index, String tableName, boolean isUnique) {
        def indexName = index.name
        def qualifiedName = prepareQualifiedName(connectionData, [tableName, indexName])
        def columns = []
        for(keyFeature in index.keyFeatures) {            
            columns += AtlasReferredEntity.createReferredEntity(TypeName.rdbms_column, AtlasEntity.prepareQualifiedName(connectionData, [tableName, keyFeature.column.name]))
        }
        
        def tableAttributes = [
            "table": AtlasReferredEntity.createReferredEntity(TypeName.rdbms_table, prepareQualifiedName(connectionData, [tableName])),
            "name": indexName,
            "qualifiedName": qualifiedName,
            "index_type": "NORMAL",
            "isUnique": isUnique,
            "columns": columns
        ]
        return new AtlasEntity(TypeName.rdbms_index, tableAttributes)        
    }
    
    def static AtlasEntity createFKEntity(Map connectionData, Map foreinKey, String tableName) {
        def indexName = foreinKey.name
        def qualifiedName = prepareQualifiedName(connectionData, [tableName, indexName])
        def keyColumns = []
        def referencesColumns = [] 
        for(keyFeature in foreinKey.keyFeatures) {
            referencesColumns += AtlasReferredEntity.createReferredEntity(TypeName.rdbms_column, AtlasEntity.prepareQualifiedName(connectionData, [tableName, keyFeature.column.name]))
        }
        
        for(keyFeature in foreinKey.target.primaryKey.keyFeatures) {
            keyColumns += AtlasReferredEntity.createReferredEntity(TypeName.rdbms_column, AtlasEntity.prepareQualifiedName(connectionData, [foreinKey.target.name, keyFeature.column.name]))
        }
        
        def tableAttributes = [
            "table": AtlasReferredEntity.createReferredEntity(TypeName.rdbms_table, prepareQualifiedName(connectionData, [tableName])),
            "name": indexName,
            "qualifiedName": qualifiedName,
            "references_columns": referencesColumns,
            "key_columns": keyColumns,
            "references_table": AtlasReferredEntity.createReferredEntity(TypeName.rdbms_table, prepareQualifiedName(connectionData, [foreinKey.target.name]))
        ]
        return new AtlasEntity(TypeName.rdbms_foreign_key, tableAttributes)
    }

    def static Map fillRdbmsInstanceAttributes(Map connectionData, String comment) {
        return [
          "rdbms_type": connectionData.databaseProductName,
          "platform": connectionData.databaseProductVersion,
          "hostname": connectionData.host,
          "port": connectionData.port,
          "comment": comment,
          "qualifiedName": getRDBMSInstanceQualifiedName(connectionData),
          "name": connectionData.host
        ]
      }
     
    public static Object prepareQualifiedNameApi(Map entity, Map params = null) {
        def jsonSlurper = new JsonSlurper()
        def java.util.Map cdata = jsonSlurper.parseText(params.connectionData)
        def el = jsonSlurper.parseText(params.elements)
        return [result: prepareQualifiedName(cdata as Map, el).toString()]
    }
    
    public static Object prepareTablesQualifiedNamesApi(Map entity, Map params = null) {
        def jsonSlurper = new JsonSlurper()
        def java.util.Map cdata = jsonSlurper.parseText(params.connectionData)
        def el = jsonSlurper.parseText(params.elements)
        def result = [:]
        el.forEach{
            result.put(it, prepareQualifiedName(cdata as Map, [it]).toString()) 
        }
        return [result: result]
    }
    
    def static Map<String, Object> fillDBAttributes(Map connectionData) {
      return [
        "prodOrOther": connectionData.databaseProductName,
        "instance": AtlasReferredEntity.createReferredEntity(TypeName.rdbms_instance, getRDBMSInstanceQualifiedName(connectionData)),
        "qualifiedName": prepareQualifiedName(connectionData, []),
        "name": connectionData.schema
      ]
    }
  
    def static String prepareQualifiedName(Map connectionData, List elements) {
      def el = [connectionData.schema].plus(elements)
      "${el.join(".")}@${connectionData.host}.${connectionData.port}.${connectionData.databaseProductName}"
    }
  
    def static String getRDBMSInstanceQualifiedName(Map<String, Object> connectionData) {
      return "${connectionData.host}.${connectionData.port}.${connectionData.databaseProductName}"
    }
    
    def static AtlasEntity createTableEntity(Map connectionData, Map table, createRefToDb = true) {
        def tableAttributes = [
            "qualifiedName": prepareQualifiedName(connectionData, [table.name]),
            "name": table.name,
            "comment": table.description,
            "type": table._type_ == "rel.Table" ? "TABLE" : "VIEW"
            ]
        if(createRefToDb == true) {
            tableAttributes.db = AtlasReferredEntity.createReferredEntity(TypeName.rdbms_db, prepareQualifiedName(connectionData, []))
        }
        return new AtlasEntity(TypeName.rdbms_table, tableAttributes)
    }
    
    def static AtlasEntity createHiveTableEntity(Map table) {
        def tableAttributes = [
            "qualifiedName": "default." + table.name + "@HDP",
            "name": table.name,
            "comment": table.description,
            "type": table._type_ == "rel.Table" ? "TABLE" : "VIEW"
            ]
        tableAttributes.db = AtlasReferredEntity.createReferredEntity(TypeName.hive_db, "default@HDP")
        return new AtlasEntity(TypeName.hive_table, tableAttributes)
    }
      
    def static String getProcessQualifiedName(transformation) {
        return transformation.project.name + "." + transformation.name
    }
    
    def static AtlasEntity createProcessEntity(Map transformation) {
      def entityAttributes = [
          "qualifiedName": getProcessQualifiedName(transformation),
          "name": transformation.name,
          "comment": ""
          ]
      return new AtlasEntity(TypeName.Process, entityAttributes)
    }
    
    def public static evalStr(text, step) {        
        
        def binding = ["step": step]
        
        def engine = new SimpleTemplateEngine()
        engine.createTemplate(text).make(binding).toString()        
    }
    
    def private static String getStepQualifiedName(step, transformation, atlas) {
        def qnames = atlas.qnamePatterns
        if(qnames != null) {
            def pattern = qnames.find{ it.name == step._type_}
            if(pattern != null) { 
                def s = AtlasEntity.evalStr(pattern.pattern, step)
                return s
            }
        }
        def result = transformation.name + "." + step.name
        if(step._type_ == "etl.LocalSource" || step._type_ == "etl.LocalTarget" || step._type_ == "etl.CSVSource" || step._type_ == "etl.XMLSource" || step._type_ == "etl.AVROSource" || step._type_ == "etl.CSVTarget" || step._type_ == "etl.XMLTarget") {
            result = getPath(step)
        }
        return result
    }
    
    def static TypeName getStepAtlasType(step) {
        if(step._type_ == "etl.LocalSource" || step._type_ == "etl.LocalTarget") {
            return TypeName.hdfs_path  
        }
        if(step._type_ == "etl.CSVSource" || step._type_ == "etl.XMLSource" || step._type_ == "etl.AVROSource" || step._type_ == "etl.CSVTarget" || step._type_ == "etl.XMLTarget") {
            return step.hdfs == true ? TypeName.hdfs_path : TypeName.fs_path
        }
    }
    
    def private static getPath(step) {
        if(step._type_ == "etl.LocalSource" || step._type_ == "etl.LocalTarget") {
            return step.localFileName
        }
        return step.path
    }
    
    def static boolean isFileEntity(_type_) {
        _type_ == "etl.LocalSource" || _type_ == "etl.CSVSource" || _type_ == "etl.XMLSource" || _type_ == "etl.AVROSource"
    }
    
    def static AtlasEntity createLocalFileEntity(Map step, Map transformation, Map atlas) {
        def extendedAttributes = [:]
        
        if(isFileEntity(step._type_)) {
            def port = step.outputPort
            for(field in port.fields) {
                extendedAttributes[field.name] = field.dataTypeDomain.toString()
            }
        }
        if(step._type_ == "etl.LocalTarget" || step._type_ == "etl.CSVTarget" || step._type_ == "etl.XMLTarget") {
            def port = step.inputFieldsMapping
            for(fieldInput in port) {
                def field = step.inputPort.fields.find{it.name == fieldInput.inputFieldName} 
                extendedAttributes[fieldInput.targetColumnName] = field?.dataTypeDomain.toString()
            }
        }
        
        def entityAttributes = [
            "qualifiedName": getStepQualifiedName(step, transformation, atlas),
            "name": getPath(step),
            "path": getPath(step),
            "comment": "",
            "description": JsonOutput.toJson(extendedAttributes)
            ]
            
        def result = new AtlasEntity(getStepAtlasType(step), entityAttributes)
        
        return result
      }
  
}

