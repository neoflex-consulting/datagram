package MetaServer.rt

import groovy.json.JsonSlurper
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import ru.neoflex.meta.model.Database

import org.mlflow.tracking.*
/* protected region MetaServer.rtLivyServer.inport on begin */
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
        def client = new MlflowClient("http://"  + mlflow.host + ":" + mlflow.port);
        def exps = client.listExperiments();
        return [];
    }
}
