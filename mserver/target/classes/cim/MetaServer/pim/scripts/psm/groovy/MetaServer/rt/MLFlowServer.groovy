package MetaServer.rt

import org.apache.commons.logging.Log

import ru.neoflex.meta.utils.SymmetricCipher
import org.apache.commons.logging.LogFactory
import ru.neoflex.meta.model.Database
import groovy.json.JsonSlurper
import MetaServer.utils.REST
import com.google.common.base.Strings
import groovyx.net.http.ContentType
import org.apache.commons.io.IOUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus

/* protected region MetaServer.rtLivyServer.inport on begin */

import org.springframework.http.ResponseEntity
import org.springframework.web.multipart.MultipartFile
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.Context
import ru.neoflex.meta.utils.ZipUtils

import java.util.zip.ZipOutputStream

class MLFlowServer {


    private final static Log logger = LogFactory.getLog(Airflow.class)
    
    public static getConnection(mlflow, path) {
        URL url = new URL("http://" + mlflow.host.replaceAll("/\\z", "") + ":" + mlflow.port.replaceAll("/\\z", "") + path);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestProperty("X-Requested-By", InetAddress.getLocalHost().getHostAddress());
        return con;
    }



    public static Object testConnection(Map entity, Map params = null) {
        def db = Database.new
        def mlflow = db.get(entity)
        
        HttpURLConnection con = getConnection(mlflow, '/');
        con.setRequestMethod("GET");
        con.connect();
        def statusCode = con.getResponseCode();
        def statusDescr = con.getResponseMessage();
        return [status: statusDescr, code : statusCode]
    }



    public static Object listExperiments(Map entity, Map params = null){

        def jsonSlurper = new JsonSlurper()
        def object = jsonSlurper.parseText(params.get("entity"))
        def db = Database.new
        def mlflow = db.get(object)


        def experimentsListUrl = "/api/2.0/mlflow/experiments/list";
        def runsListUrl = "/api/2.0/mlflow/runs/search";
        def exps = REST.getHTTPClient("http://" + mlflow.host+ ":"+ mlflow.port).get(
                path: experimentsListUrl,
                requestContentType: ContentType.JSON,
                contentType: ContentType.JSON)?.reader

        def expsForReturns = []
        def experimentsValue= exps.values()[0]
        println(experimentsValue)


        for(e in experimentsValue){
            //def q = ["experiment_ids": [e.experiment_id], "filter": "", "run_view_type": "ACTIVE_ONLY", "max_results": 100, "order_by": []]
            def q = ["experiment_ids": [ e.experiment_id ]]
            def exp = REST.getHTTPClient("http://" + mlflow.host+ ":"+ mlflow.port).post(
                    path: runsListUrl,
                    body: q,
                    requestContentType: ContentType.JSON,
                    contentType: ContentType.JSON)?.reader
            expsForReturns << exp
            e["runs"] = exp["runs"];
        }
        println(experimentsValue)
        return experimentsValue;
    }
}
