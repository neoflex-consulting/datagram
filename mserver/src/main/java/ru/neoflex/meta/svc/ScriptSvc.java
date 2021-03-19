package ru.neoflex.meta.svc;

import groovy.lang.Binding;
import groovy.util.GroovyScriptEngine;
import groovy.util.ResourceException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import ru.neoflex.meta.utils.MetaResource;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

/**
 * Created by orlov on 14.04.2015.
 */
@Service
@DependsOn({
        "ru.neoflex.meta.svc.MSpaceSvc",
        "ru.neoflex.meta.svc.TeneoSvc",
        "ru.neoflex.meta.svc.ContextSvc"
})
public class ScriptSvc extends BaseSvc {
    private final static Log logger = LogFactory.getLog(ScriptSvc.class);
    public static final String SCRIPT_DIR = "cim/MetaServer/pim/scripts/psm/groovy";
    @Autowired
    private ContextSvc contextSvc;

    private GroovyScriptEngine groovyScriptEngine;

    private File getMSpaceScriptDir() {
        return new File(getMSpaceDir(), SCRIPT_DIR);
    }

    private File getDeployScriptDir() {
        return new File(getDeployDir(), SCRIPT_DIR);
    }

    private File getTempScriptDir() {
        return new File(getTempDir(), SCRIPT_DIR);
    }

    public void configure(URL[] urls) {
        if (groovyScriptEngine == null) {
            try {
                ArrayList<URL> arr = new ArrayList<URL>(/*Arrays.asList(urls)*/);
                arr.add(new URL(getTempScriptDir().toURI().toString() + "/"));
                arr.add(new URL(getDeployScriptDir().toURI().toString() + "/"));
                arr.add(new URL(getMSpaceScriptDir().toURI().toString() + "/"));
                groovyScriptEngine = new GroovyScriptEngine(arr.toArray(new URL[0])) {
                    @Override
                    public URLConnection getResourceConnection(String name) throws ResourceException {
                        String fullScriptName = SCRIPT_DIR + "/" + name;
                        MetaResource.export(fullScriptName, getTempDir(), fullScriptName, true);
                        return super.getResourceConnection(name);
                    }
                };
                CompilerConfiguration compilerConfiguration = new CompilerConfiguration();
                compilerConfiguration.setSourceEncoding("UTF-8");
                groovyScriptEngine.setConfig(compilerConfiguration);
            } catch (Exception e) {
                logger.error(e);
                throw new RuntimeException(e);
            }
        }
    }

    public Object run(String scriptName, Map variables) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            try {
                Binding binding = new Binding(variables);
                return  groovyScriptEngine.run(scriptName, binding);
            }
            finally {
                Thread.currentThread().setContextClassLoader(classLoader);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Object runMethod(String scriptName, String method, Map entity, Map params) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            try {
                Binding binding = new Binding();
                binding.setVariable("entity", entity);
                Class scriptClass = groovyScriptEngine.loadScriptByName(scriptName);
                Object scriptInstance = scriptClass.newInstance();
                Method declaredMethod = scriptClass.getDeclaredMethod(method, new Class[] {Map.class, Map.class} );
                return  declaredMethod.invoke(scriptInstance, new Object[]{entity, params});
            }
            finally {
                Thread.currentThread().setContextClassLoader(classLoader);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Object run(String scriptName) {
        return run(scriptName, new HashMap());
    }

    public void runAll(final String catalogName) {
        Set<String> fileNames = new TreeSet<String>();
        String fullCatalog = SCRIPT_DIR + "/" + catalogName;
        Resource[] resources = contextSvc.getResourceSvc().loadClasspathResources(fullCatalog + "/*.groovy");
        for (Resource resource: resources) {
            fileNames.add(resource.getFilename());
        }
        for (final String fileName: fileNames) {
            if (fileName.startsWith("~")) continue;
            contextSvc.inContext(new Runnable() {
                @Override
                public void run() {
                    ScriptSvc.this.run(catalogName + "/" + fileName);
                }
            });
        }
    }
}
