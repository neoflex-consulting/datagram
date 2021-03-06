package MetaServer.rt

import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.util.Map

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import MetaServer.utils.AtlasEntity
import MetaServer.utils.DBConnection
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import ru.neoflex.meta.model.Database

/* protected region MetaServer.rtAtlasScheme.inport end */

class AtlasScheme {
    private final static Log logger = LogFactory.getLog(AtlasScheme.class)
    /* protected region MetaServer.rtAtlasScheme.statics on begin */
    def static partSize = 1000
    
    public static Object buildConnectionDataApi(Map entity, Map params = null) {
        def jsonSlurper = new JsonSlurper()
        return buildConnectionData(jsonSlurper.parseText(params.connectionData), jsonSlurper.parseText(params.deployment))
    }
    
    public static Map buildConnectionData(Map connectionData, deployment) {
        String url = deployment.connection.url
        String cleanURI
        if (url.indexOf(":oracle:") >= 0) {
            cleanURI = url.replace("@", "//").substring(12)
        } else {
            cleanURI = url.substring(5)
        }
        URI uri = URI.create(cleanURI)
        
        connectionData.host = uri.host
        connectionData.port = uri.port
        
        Connection connection = DBConnection.getConnection(deployment.connection.url, deployment.connection.user, deployment.connection.password)

        connectionData.databaseProductName = connection.getMetaData().getDatabaseProductName()
        connectionData.databaseProductVersion = connection.getMetaData().getDatabaseProductVersion()
        connectionData.schema = deployment.connection.schema
        return connectionData
    }
    
    public static Object initConnectionData(Map entity, Map params = null) {
        return initConnectionData(params.schemeName)        
    }
    
    public static Map initConnectionData(String schema) {
        def Map result = [
            host: "host",
            port: 8080,
            databaseProductName: "databaseProductName",
            databaseProductVersion: "databaseProductVersion",
            schema: schema]
        return result
    }
    
    public static Object publish(Map entity, Map params = null) {
        
        def db = Database.new
        def atlasScheme = db.get(entity)
        def atlas = atlasScheme.atlas
        
        logger.info("Load schema: " + atlasScheme.name)
        
        def connectionData = initConnectionData(atlasScheme.name)
        
        def softwareSystemList = db.session.createQuery("from rt.SoftwareSystem where project.e_id=${atlas.project.e_id} and scheme.e_id=${atlasScheme.scheme.e_id}").list()
        if(softwareSystemList.size() > 0) {
            logger.info("Load instance info from softwareSystem.defaultDeployment")
            def softwareSystem = softwareSystemList.get(0) 
            if(softwareSystem.defaultDeployment) {
                if(softwareSystem.defaultDeployment.connection) {
                    try {
                        buildConnectionData(connectionData, softwareSystem.defaultDeployment)
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                }
                
            }
        }
                        
        def instance = new AtlasEntity(AtlasEntity.TypeName.rdbms_instance, AtlasEntity.fillRdbmsInstanceAttributes(connectionData, "from datagram"))
        logger.info("Import rdbms_instance info")
        AtlasScheme.importData(atlas, [instance])
        def database = new AtlasEntity(AtlasEntity.TypeName.rdbms_db, AtlasEntity.fillDBAttributes(connectionData))
        logger.info("Import rdbms_db info")
        AtlasScheme.importData(atlas, [database])        
        
        def tables = []
        for(schemaTable in atlasScheme.scheme.tables) {
            def table = AtlasEntity.createTableEntity(connectionData, schemaTable)
            tables += table
        }
        logger.info("Import rdbms_table info")
        AtlasScheme.importData(atlas, tables)
        def cols = []
        for(schemaTable in atlasScheme.scheme.tables) {
            for(tableColumn in schemaTable.columns) {
                def column = AtlasEntity.createColumnEntity(connectionData, tableColumn)
                cols += column
            }
        }

        logger.info("Import rdbms_column info")
        AtlasScheme.importData(atlas, cols)
        
        def indexes = []

        for(schemaTable in atlasScheme.scheme.tables) {
            for(idx in schemaTable.indexes) {
                indexes += AtlasEntity.createIndexEntity(connectionData, idx, schemaTable.name, idx.isUnique)
            }
        }
        
        for(schemaTable in atlasScheme.scheme.tables) {
            if(schemaTable.primaryKey != null) {
                indexes += AtlasEntity.createIndexEntity(connectionData, schemaTable.primaryKey, schemaTable.name, true)
            }
        }
        
        for(schemaTable in atlasScheme.scheme.tables) {
            for(fk in schemaTable.foreignKeys) {
                indexes += AtlasEntity.createFKEntity(connectionData, fk, schemaTable.name)
            }
        }
        logger.info("Import rdbms_index info")
        def resp = AtlasScheme.importData(atlas, indexes)
        
        logger.info("Check deleted rdbms_index")
        AtlasScheme.bulkDeleteEntities(atlas, getDeletedGuids(atlas, database.getQueryLikeValue(), indexes, AtlasEntity.TypeName.rdbms_index))
        logger.info("Check deleted rdbms_column")
        AtlasScheme.bulkDeleteEntities(atlas, getDeletedGuids(atlas, database.getQueryLikeValue(), cols, AtlasEntity.TypeName.rdbms_column))
        logger.info("Check deleted rdbms_table")
        AtlasScheme.bulkDeleteEntities(atlas, getDeletedGuids(atlas, database.getQueryLikeValue(), tables, AtlasEntity.TypeName.rdbms_table))
        logger.info("Check deleted rdbms_db")
        AtlasScheme.bulkDeleteEntities(atlas, getDeletedGuids(atlas, database.getQueryLikeValue(), [database], AtlasEntity.TypeName.rdbms_db))
        logger.info("Check deleted rdbms_instance")
        AtlasScheme.bulkDeleteEntities(atlas, getDeletedGuids(atlas, instance.getQueryLikeValue(), [instance], AtlasEntity.TypeName.rdbms_instance))

        logger.info("Finished!")
        return [result: "Finished!"]
    }   
    
    private static getConnection(atlas, path) {
        logger.info("Get connection for: " + path)
        URL url = new URL(atlas.http + path);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        
        String encoded = Base64.getEncoder().encodeToString((atlas.userName + ":" + atlas.password).getBytes(StandardCharsets.UTF_8));  //Java 8
        con.setRequestProperty("Authorization", "Basic " + encoded);
        con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        con.setRequestProperty("X-Requested-By", InetAddress.getLocalHost().getHostAddress());
        return con;
    }
    
    public static List importData(Map atlas, entities) {
        def partSize = 1000
        def resp = []
        if(entities.size > 0) {
            def i            
            for (i = 0; i < entities.size; i = i + partSize) {
                def ent = entities[i..((i + partSize) > entities.size - 1 ? entities.size - 1 : i + partSize) ]
                def rb = JsonOutput.toJson(["entities": ent]) 
                logger.info("Import from " + i + " to " + (i + partSize >= entities.size ? entities.size : i + partSize) + " total " + entities.size)       
                try {

                    HttpURLConnection con = getConnection(atlas, '/api/atlas/v2/entity/bulk');
                    con.setRequestMethod("POST");
                    con.setDoOutput(true);
                    
                    DataOutputStream wr = new DataOutputStream(con.getOutputStream());
                    wr.writeBytes(rb);
                    wr.flush();
                    wr.close();
                    def res = readOutput(con)
                    resp.add(res)
                    int responseCode = con.getResponseCode();
                } catch (Throwable e) {
                    logger.error("Exception while send data, try to resend", e)
                    throw e;
                }
            }            
        }
        return resp
    }
    
    public static updateEntity(Map atlas, entity) {        
        HttpURLConnection con = getConnection(atlas, '/api/atlas/v2/entity/');
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        def resp = []
        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
        def rb = JsonOutput.toJson(entity)
        wr.writeBytes(rb);
        wr.flush();
        wr.close();
        def res = readOutput(con)
        resp.add(res)
        int responseCode = con.getResponseCode();
        return resp
    }
    
    public static Object searchByTypeNameApi(Map entity, Map params = null, operator = "=") {
        def jsonSlurper = new JsonSlurper()
        def r = searchByTypeName(jsonSlurper.parseText(params.atlas), params.qname, params.typeName, 0, operator)
        return [result: r]

    }
    
    public static Object readOutput(con) {
        BufferedReader input = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();
        
        while ((inputLine = input.readLine()) != null) {
            response.append(inputLine);
        }
        input.close();
        
        def jsonSlurper = new JsonSlurper()
        jsonSlurper.parseText(response.toString())
    }
    
    public static searchByTypeName(Map atlas, String queryParam, typeName, Integer offset, operator = "=") {
        def query = "where qualifiedName " + operator + " '" + queryParam + "' and __state = 'ACTIVE'";
        def parameters = "typeName=" + typeName + "&limit=" + AtlasScheme.partSize + "&offset=" + offset + "&query=" + URLEncoder.encode(query, "UTF-8");
        HttpURLConnection con = getConnection(atlas, '/api/atlas/v2/search/dsl?' + parameters);
        con.setRequestMethod("GET");
        
        logger.info("Search for " + typeName + " from offset " + offset)
        def rm = readOutput(con)        
        return rm.entities
    }

    public static getByGuid(Map atlas, String guid) {
        HttpURLConnection con = getConnection(atlas, '/api/atlas/v2/entity/guid/' + guid);
        con.setRequestMethod("GET");
        
        logger.info("Get by guid")
        def rm = readOutput(con)
        return rm
    }
    
    def static bulkDeleteEntities(atlas, allGuids) {
        if(!allGuids.isEmpty()) {
            def i
            def resp
            for (i = 0; i < allGuids.size; i = i + partSize) {
                def guids = allGuids[i..((i + partSize) > allGuids.size - 1 ? allGuids.size - 1 : i + partSize) ]
                logger.info("Delete entities " + i + " (" + allGuids.size + ")")
                def query = JsonOutput.toJson([guid: guids]);
                HttpURLConnection con = getConnection(atlas, '/api/atlas/v2/entity/bulk?query=' + URLEncoder.encode(query, "UTF-8"));
                con.setRequestMethod("DELETE");
                con.connect();
            }
            return resp
        }
    }
    
    def static buildGuids(guids, entities, dbQualifiedNames) {
        if (entities.size == 0) {
            return guids
        } else {
            def entity = entities.head()
            if (dbQualifiedNames.contains(entity.attributes.qualifiedName)) {
                AtlasScheme.buildGuids(guids, entities.tail(), dbQualifiedNames)
            } else {
                guids.add(entity.guid)
                return AtlasScheme.buildGuids(guids, entities.tail(), dbQualifiedNames)
            }
        }
    }
        
    def static searchPartition(atlas, queryParam, dbQualifiedNames, typeName, guids, offset) {
        def existEntities = searchByTypeName(atlas, queryParam, typeName, offset, "LIKE")
        if (existEntities == null || existEntities.size == 0) {
            return guids
        } else {
            if (existEntities.size < AtlasScheme.partSize) {
                return AtlasScheme.buildGuids(guids, existEntities, dbQualifiedNames)
            } else {
                AtlasScheme.buildGuids(guids, existEntities, dbQualifiedNames)
                return AtlasScheme.searchPartition(atlas, queryParam, dbQualifiedNames, typeName, guids, offset + AtlasScheme.partSize)
            }
        }
    }
    
    public static getDeletedGuids(atlas, queryParam, dbEntities, typeName) {
        def guids = []
        def dbQualifiedNames = dbEntities.collect{ it.ggetQualifiedName() }
        searchPartition(atlas, queryParam, dbQualifiedNames, typeName, guids, 0)
        guids
    }
    /* protected region MetaServer.rtAtlasScheme.statics end */
}
