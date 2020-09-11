package ru.neoflex.meta.svc;

/**
 * Created by orlov on 17.12.2015.
 */

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.neoflex.meta.utils.MetaResource;
import scala.tools.nsc.Settings;
import scala.tools.nsc.interpreter.IMain;
import scala.tools.nsc.settings.MutableSettings;

import javax.script.CompiledScript;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ScalaSvc extends BaseSvc {
    private final static Log logger = LogFactory.getLog(ScalaSvc.class);
    @Autowired
    private ClassLoaderSvc classLoaderSvc;

    public void eval(String code, List<Map<String, Object>> vars, Map<String, Object> result) {
        Settings settings = new Settings();
        MutableSettings.BooleanSetting usejavacp = (MutableSettings.BooleanSetting)settings.usejavacp();
        usejavacp.tryToSetFromPropertyValue("true");
        //settings.embeddedDefaults(classLoaderSvc.getClassLoader());
        for (URL url: classLoaderSvc.getUrlList()) {
            String file = url.getFile();
            settings.classpath().append(file);
            logger.debug(file);
        }
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        IMain interpreter = new IMain(settings, printWriter);
        scala.collection.immutable.List nilList = scala.collection.immutable.Nil$.MODULE$;
        if (vars != null) {
            for (Map<String, Object> var: vars) {
                interpreter.bind((String)var.get("name"), (String)var.get("type"), var.get("value"), nilList);
            }
        }
        try {
            Object value = interpreter.eval(code);
            result.put("value", value);
            result.put("result", true);
            result.put("message", stringWriter.getBuffer().toString());
        }
        catch (Exception e) {
            result.put("value", null);
            result.put("result", false);
            result.put("message", e.getMessage() + ":\r\n" + stringWriter.getBuffer().toString());
            logger.error(result.get("message"));
        }
    }

    public void compile(String code, List<Map<String, Object>> vars, Map<String, Object> result) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            code = "import ru.neoflex.meta.etl.functions._\n" + code;
            Settings settings = new Settings();
            MutableSettings.BooleanSetting usejavacp = (MutableSettings.BooleanSetting)settings.usejavacp();
            usejavacp.tryToSetFromPropertyValue("true");
            for (URL url: classLoaderSvc.getUrlList()) {
                String file = url.getFile();
                settings.classpath().append(file);
                logger.debug(file);
            }
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            IMain interpreter = new IMain(settings, printWriter){
                public ClassLoader parentClassLoader() {
                    return classLoaderSvc.getClassLoader();
                }
            };
            interpreter.setContextClassLoader();
            scala.collection.immutable.List nilList = scala.collection.immutable.Nil$.MODULE$;
            if (vars != null) {
                for (Map<String, Object> var: vars) {
                    interpreter.bind((String)var.get("name"), (String)var.get("type"), var.get("value"), nilList);
                }
            }
            try {
                CompiledScript script = interpreter.compile(code);
                result.put("result", true);
                result.put("message", stringWriter.getBuffer().toString());
                logger.debug(result.get("message"));
            }
            catch (Exception e) {
                result.put("result", false);
                result.put("message", e.getMessage() + ":\r\n" + stringWriter.getBuffer().toString());
                logger.error(result.get("message"));
            }
        }
        finally {
            Thread.currentThread().setContextClassLoader(classLoader);
        }
    }
}
