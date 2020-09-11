package ru.neoflex.meta.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.neoflex.meta.model.Database;
import ru.neoflex.meta.svc.ContextSvc;
import ru.neoflex.meta.svc.ScriptSvc;
import ru.neoflex.meta.utils.JSONHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by orlov on 25.08.2015.
 */
@Service
public class JMSEventHandler {
    private final static Log logger = LogFactory.getLog(JMSEventHandler.class);
    @Autowired
    ContextSvc contextSvc;
    @Autowired
    ScriptSvc scriptSvc;
    Map scriptConfig;
    Map parameters;
    String defaultScript;    

    public Object onMessage(final String message) {
        logger.debug("JMS message: " + message);
        System.out.println("JMS message: " + message);
        final Object[] result = new Object[] {null};
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                ObjectMapper mapper = new ObjectMapper();
                try {
                    Map<String, Object> map = mapper.readValue(message, new TypeReference<Map<String, Object>>() {
                    });
                    String type = (String) map.get("_type_");
                    String scriptName = (String) scriptConfig.get(type);
                    if (scriptName == null) {
                        scriptName = defaultScript;
                    }
                    Map params = new HashMap();
                    params.put("message", map);
                    params.put("parameters", parameters);
                    result[0] = scriptSvc.run(scriptName, params);
                } catch (Exception e) {
                    logger.error("Error handling message: " + message, e);
                }
            }
        });
        return result[0];
    }

    public void setScriptConfig(Map scriptConfig) {
        this.scriptConfig = scriptConfig;
    }
    
    public void setParameters(Map parameters) {
    	this.parameters = parameters;
    }    

    public void setDefaultScript(String  defaultScript) {
        this.defaultScript = defaultScript;
    }

    public void setContextSvc(ContextSvc contextSvc) {
        this.contextSvc = contextSvc;
    }

    public void setScriptSvc(ScriptSvc scriptSvc) {
        this.scriptSvc = scriptSvc;
    }

}

