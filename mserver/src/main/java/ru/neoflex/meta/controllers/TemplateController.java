package ru.neoflex.meta.controllers;

import org.eclipse.epsilon.eol.models.IModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;
import ru.neoflex.meta.svc.*;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Created by orlov on 14.04.2015.
 */

@Controller
@RequestMapping("/")
public class  TemplateController {
    static final String TEMPLATES_HTML = "/templates/html";
    static final String SCRIPTS = "/scripts";
    static final String APPLICATIONS = "/applications";
    @Autowired
    ContextSvc contextSvc;
    @Autowired
    TemplateSvc templateSvc;
    @Autowired
    ScriptSvc scriptSvc;
    @Autowired
    FMPPSvc fmppSvc;
    @Autowired
    EpsilonSvc epsilonSvc;
    @Autowired
    AntSvc antSvc;

    @RequestMapping(value= "/**/*.xml", method= RequestMethod.GET, produces={"application/json"})
    @ResponseBody
    public int runAnt(@RequestParam final Map<String,Object> requestParams, HttpServletRequest request) {
        final String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        final String templateName = path.substring(1);
        final int[] result =  {0};
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                result[0] = antSvc.run(templateName, requestParams);
            }
        });
        return result[0];
    }

    @RequestMapping(value= "/**/*.etl", method= RequestMethod.GET, produces={"application/json"})
    @ResponseBody
    public Object processEtl(@RequestParam final Map<String,Object> requestParams, HttpServletRequest request) {
        final String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        final String templateName = path.substring(1);
        requestParams.put("request", request);
        final Object[] result =  new Object[1];
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                result[0] = epsilonSvc.executeEtl(templateName, requestParams, new ArrayList<IModel>());
            }
        });
        return result[0];
    }

    @RequestMapping(value= "/**/*.egx", method= RequestMethod.GET, produces={"application/json"})
    @ResponseBody
    public Object processEgx(@RequestParam final Map<String,Object> requestParams, HttpServletRequest request) {
        final String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        final String templateName = path.substring(1);
        requestParams.put("request", request);
        final Object[] result =  new Object[1];
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                result[0] = epsilonSvc.executeEgx(templateName, requestParams, new ArrayList<IModel>());
            }
        });
        return result[0];
    }

    @RequestMapping(value= "/**/*.ecl", method= RequestMethod.GET, produces={"application/json"})
    @ResponseBody
    public Object processEcl(@RequestParam final Map<String,Object> requestParams, HttpServletRequest request) {
        final String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        final String templateName = path.substring(1);
        requestParams.put("request", request);
        final Object[] result =  new Object[1];
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                result[0] = epsilonSvc.executeEcl(templateName, requestParams, new ArrayList<IModel>());
            }
        });
        return result[0];
    }

    @RequestMapping(value= "/**/*.egl", method= RequestMethod.GET, produces={"text/plain", "text/html"})
    @ResponseBody
    public String processEgl(@RequestParam final Map<String,Object> requestParams, HttpServletRequest request) {
        final String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        final String templateName = path.substring(1);
        requestParams.put("request", request);
        final String[] result =  new String[1];
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                result[0] = epsilonSvc.executeEgl(templateName, requestParams, new LinkedList<IModel>());
            }
        });
        return result[0];
    }

    @RequestMapping(value= "/**/*.eol", method= RequestMethod.GET, produces={"application/json"})
    @ResponseBody
    public Object processEol(@RequestParam final Map<String,Object> requestParams, HttpServletRequest request) {
        final String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        final String templateName = path.substring(1);
        requestParams.put("request", request);
        final Object[] result =  new Object[1];
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                result[0] = epsilonSvc.executeEol(templateName, requestParams, new ArrayList<IModel>());
            }
        });
        return result[0];
    }

    @RequestMapping(value= "/**/*.ftl", method= RequestMethod.GET, produces={"text/plain", "text/html"})
    @ResponseBody
    public String processTemplate(@RequestParam final Map<String,Object> requestParams, HttpServletRequest request) {
        final String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        final String templateName = path.substring(1);
        final String[] result =  new String[1];
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                result[0] = templateSvc.processTemplate(templateName, requestParams);
            }
        });
        return result[0];
    }

    @RequestMapping(value= "/**/*.groovy", method= RequestMethod.GET, produces={"application/json"})
    @ResponseBody
    public Object runScript(@RequestParam final Map<String,Object> requestParams, HttpServletRequest request) {
        final String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        final String scriptName = path.substring(SCRIPTS.length() + 1);
        final Object[] result =  new Object[1];
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                result[0] = scriptSvc.run(scriptName, requestParams);
            }
        });
        return result[0];
    }

    @RequestMapping(value= "/**/*.groovy", method= RequestMethod.POST, produces={"application/json"})
    @ResponseBody
    public Object updateModel(@RequestBody final Map<String,Object> model, HttpServletRequest request) {
        final String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        final String scriptName = path.substring(1);
        final Map[] result =  new Map[1];
        result[0] = new HashMap<>();
        result[0].put("model", model);
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                result[0] = (Map) scriptSvc.run(scriptName, result[0]);
            }
        });
        return result[0];
    }

    @RequestMapping(value= "/**/*.fmpp", method= RequestMethod.GET, produces={"application/json"})
    @ResponseBody
    public Map runFMPP(@RequestParam final Map<String,Object> requestParams, HttpServletRequest request) {
        final String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        final String scriptName = path.substring(1);
        final Map[] result =  new Map[1];
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                result[0] = fmppSvc.execute(scriptName, requestParams);
            }
        });
        return result[0];
    }
}
