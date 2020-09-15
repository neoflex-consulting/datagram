package MetaServer.rt

import MetaServer.utils.AtlasEntity
//import MetaServer.utils.AtlasEntity.TypeName
import MetaServer.utils.DBConnection
import com.google.common.base.Strings
import groovyx.net.http.ContentType
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpStatus
import java.sql.Connection
import java.util.Map

import MetaServer.utils.REST
import groovy.json.JsonOutput
import groovyx.net.http.RESTClient
import org.apache.http.auth.AuthScope
import org.apache.http.HttpHost

import groovyx.net.http.HTTPBuilder
import org.apache.http.client.config.RequestConfig
import org.apache.http.config.SocketConfig
import org.apache.http.conn.ConnectTimeoutException
import org.apache.http.impl.client.HttpClients
import org.apache.http.client.CredentialsProvider
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.auth.UsernamePasswordCredentials



/* protected region MetaServer.rtLivyServer.inport on begin */

import org.springframework.http.ResponseEntity
import ru.neoflex.meta.model.Database

/* protected region MetaServer.rtAtlasScheme.inport end */

class Atlas {
    private final static Log logger = LogFactory.getLog(Atlas.class)
    def public static publishSchemes(Map entity, Map params = null){
        
        def db = Database.new
        def atlas = db.get(entity)

        def schemas = db.select("select s from rt.AtlasScheme s where s.atlas.e_id=:atlas", ["atlas": atlas.e_id])
        logger.info("Founded schemas: " + schemas.size)
        for(atlasScheme in schemas) {
            AtlasScheme.publish(atlasScheme)
        }
        
        return [result: "Finished!"]
    }
    
    public static updateForeingKeyType(Map atlas, Map params = null) {        
        def RESTClient client = REST.getHTTPClient(atlas)
        client.auth.basic(atlas.userName, atlas.password)
        
        def resp = client.get(
                path : '/api/atlas/v2/types/typedefs',
                headers: ["X-Requested-By": InetAddress.getLocalHost().getHostAddress(), "Connection-Timeout": 12000],
                contentType : groovyx.net.http.ContentType.JSON,
                requestContentType : groovyx.net.http.ContentType.JSON)?.reader
        def entities = resp.entityDefs
        def fkdef = entities.find {it.name == "rdbms_foreign_key"}
        def tableAttr = fkdef.attributeDefs.find {it.name == "table"}
        tableAttr.constraints = [["type": "inverseRef","params": ["attribute": "foreign_keys"]]]
        def resp2 = client.put(
            path : '/api/atlas/v2/types/typedefs',
            body: JsonOutput.toJson(resp),
            headers: ["X-Requested-By": InetAddress.getLocalHost().getHostAddress(), "Connection-Timeout": 12000],
            contentType : groovyx.net.http.ContentType.JSON,
            requestContentType : groovyx.net.http.ContentType.JSON)?.reader
        return [result: "Finished!"]        
    }
}