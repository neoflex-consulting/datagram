package MetaServer.utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.w3c.dom.Document
import org.w3c.dom.Element
import ru.neoflex.meta.utils.Context

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
/**
 * Created by orlov on 17.07.2016.
 */

class Oozie {
    static Document buildConfigFromProperties(Map<String, String> properties) {
        Objects.requireNonNull(properties);
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document oozieWf = builder.newDocument();
            Element config = oozieWf.createElement("configuration");
            oozieWf.appendChild(config);

            for (Map.Entry<String, String> e: properties.entrySet()) {
                String key = e.getKey();
                String value = e.getValue();
                config.appendChild(createPropertyNode(key, value, oozieWf));
            }

            return oozieWf;
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    static String xmlToString(Document xml) {
        Objects.requireNonNull(xml);
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(xml), new StreamResult(writer));
            String xmlString = writer.getBuffer().toString();
            println("Serialized XML: ${xmlString}");
            return xmlString;
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        }
    }

    private static Element createPropertyNode(String name, String value, Document parent) {
        Element prop = parent.createElement("property");
        Element propName = parent.createElement("name");
        propName.appendChild(parent.createTextNode(name));
        Element propVal = parent.createElement("value");
        propVal.appendChild(parent.createTextNode(value));
        prop.appendChild(propName);
        prop.appendChild(propVal);
        return prop;
    }

    public static String submitWorkflow(Map oozie /*'http://cloud.neoflex.ru:11000'*/, Map<String, String> parameters) {
        def client = REST.getSimpleHTTPClient( oozie )
        def requestBody = xmlToString(buildConfigFromProperties(parameters))
        def resp = client.post(
                path : '/oozie/v2/jobs',
                query : [action: "start"],
                body : requestBody,
                contentType : groovyx.net.http.ContentType.TEXT,
                requestContentType : groovyx.net.http.ContentType.XML)
        if (resp.status < 200 || resp.status > 300) {
            throw new RuntimeException(resp);
        }
        def body = resp.data.str
        return readAttribute(body, "id");
    }

    public static String submitWorkflow(Map oozie, File propFile, Map params) {
        Properties properties = new Properties()
        propFile.withReader("UTF-8") {
            properties.load(it)
        }
        def parameters = ["user.name": oozie.user]
        for (name in properties.stringPropertyNames()) {
            parameters.put(name, properties.getProperty(name))
        }
        if (params != null) {
            parameters.putAll(params)
        }
        return submitWorkflow(oozie, parameters)
    }

    private static String readAttribute(String body, String attribute) {
        ObjectMapper parser = new ObjectMapper();
        try {
            JsonNode obj = parser.readTree(body);
            if (obj != null && obj.isObject()) {
                return obj.get(attribute).asText();
            }
        } catch (IOException e) {
            throw new RuntimeException("Error while submitting Oozie workflow", e);
        }
        throw new RuntimeException(String.format("Error while submitting Oozie workflow, %s", body));
    }

    public static getWorkflowStatus(Map oozie, String workflowId) {
        def client = REST.getSimpleHTTPClient( oozie )
        def resp = client.get(
                path : "/oozie/v2/job/${workflowId}",
                contentType : groovyx.net.http.ContentType.TEXT,
                query : [show: "info"])
        if (resp.status < 200 || resp.status > 300) {
            throw new RuntimeException(resp);
        }
        def body = resp.data.text
        return readAttribute(body, "status");
    }

    public static getWorkflowLog(Map oozie, String workflowId) {
        def client = REST.getSimpleHTTPClient( oozie )
        def resp = client.get(
                path : "/oozie/v2/job/${workflowId}",
                contentType : groovyx.net.http.ContentType.TEXT,
                query : [show: "errorlog"])
        if (resp.status < 200 || resp.status > 300) {
            throw new RuntimeException(resp);
        }
        def log = resp.data.text
        return log
    }

    public static findByProject(Object project) {
        if (project == null) return getDefault()
        else return Context.current.session.createQuery("from rt.Oozie where project.e_id = ${project?.e_id}").uniqueResult()
    }

    public static getDefault() {
        def oozie = Context.current.session.createQuery("from rt.Oozie where isDefault = true").uniqueResult()
        if (oozie == null) {
                throw new RuntimeException("default Oozie not found")
            }
        return oozie
    }

}
