package MetaServer.rt

import org.apache.commons.logging.Log

import ru.neoflex.meta.utils.SymmetricCipher
import org.apache.commons.logging.LogFactory
import ru.neoflex.meta.model.Database
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

class Airflow {
    
    private final static Log logger = LogFactory.getLog(Airflow.class)
    
    public static getConnection(airflow, path) {
        URL url = new URL(airflow.http.replaceAll("/\\z", "") + path);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        
        //String encoded = Base64.getEncoder().encodeToString((atlas.userName + ":" + atlas.password).getBytes(StandardCharsets.UTF_8));  //Java 8
        //con.setRequestProperty("Authorization", "Basic " + encoded);
        //con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        con.setRequestProperty("X-Requested-By", InetAddress.getLocalHost().getHostAddress());
        return con;
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
    
    public static Object testConnection(Map entity, Map params = null) {
        def db = Database.new
        def airflow = db.get(entity)
        
        HttpURLConnection con = getConnection(airflow, '/test');
        con.setRequestMethod("GET");
        
        def rm = readOutput(con)
        return rm
    }
    
    public static Object getPools(Map entity, Map params = null) {
        def db = Database.new
        
        def jsonSlurper = new JsonSlurper()
        def object = jsonSlurper.parseText(params.get("entity"))
        
        def airflow = db.get(object)
        
        HttpURLConnection con = getConnection(airflow, '/pools');
        con.setRequestMethod("GET");
        
        def rm = readOutput(con)
        return rm        
    }
    
    public static Object createPool(Map entity, Map params = null) {
        def db = Database.new
        
        def jsonSlurper = new JsonSlurper()
        def object = jsonSlurper.parseText(params.get("entity"))
        
        def airflow = db.get(object)
        
        HttpURLConnection con = getConnection(airflow, '/pools');
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        
        def resp = []
        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
        def rb = JsonOutput.toJson(params.get("pool"))
        wr.writeBytes(rb);
        wr.flush();
        wr.close();
        def res = readOutput(con)
        resp.add(res)
        int responseCode = con.getResponseCode();
        return resp
    }
    
    public static Object deletePool(Map entity, Map params = null) {
        def db = Database.new
        
        def jsonSlurper = new JsonSlurper()
        def object = jsonSlurper.parseText(params.get("entity"))
        
        def airflow = db.get(object)
        
        HttpURLConnection con = getConnection(airflow, '/pools/' + params.get("pool").get("name"));
        con.setRequestMethod("DELETE");
        con.connect();
        
        con.setDoOutput(true);
        
        def resp = []
        def res = readOutput(con)
        resp.add(res)
        int responseCode = con.getResponseCode();
        return resp
    }
    
    public static Object editPool(Map entity, Map params = null) {
        deletePool(entity, params)
        createPool(entity, params)
        
    }
}
