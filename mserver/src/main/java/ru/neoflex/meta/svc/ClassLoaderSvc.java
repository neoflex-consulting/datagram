package ru.neoflex.meta.svc;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

/**
 * Created by orlov on 16.12.2015.
 */
@Service
public class ClassLoaderSvc extends BaseSvc {
    private final static Log logger = LogFactory.getLog(ClassLoaderSvc.class);
    public static final String EXTERNAL_JAVA_LIBS = "cim/MetaServer/pim/external/psm/java/lib";
    private ClassLoader classLoader;
    private List<URL> urlList = new ArrayList<>();
    @Autowired
    private ResourceSvc resourceSvc;

    @PostConstruct
    void init() throws IOException {
        File javaLibsDir = new File(getTempDir(), EXTERNAL_JAVA_LIBS);
        resourceSvc.exportDir(EXTERNAL_JAVA_LIBS, javaLibsDir);
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        for (URL url: collectURLs(classLoader, new ArrayList<URL>())) {
            String surl = url.toString();
            if (/*surl.endsWith(".jar!/") && */surl.contains("ru.neoflex.meta")) {
                logger.info(surl);
                URLConnection urlConnection = url.openConnection();
                if (urlConnection instanceof JarURLConnection) {
                    JarURLConnection jarURLConnection = (JarURLConnection) urlConnection;
                    URL jarURL = jarURLConnection.getJarFileURL();
                    String newPath = EXTERNAL_JAVA_LIBS + "/" + new File(jarURL.getFile()).getName();
                    resourceSvc.exportURL(jarURL, getTempDir(), newPath);
                }
                if (urlConnection instanceof sun.net.www.protocol.file.FileURLConnection) {
                	sun.net.www.protocol.file.FileURLConnection jarURLConnection = (sun.net.www.protocol.file.FileURLConnection) urlConnection;
                    URL jarURL = jarURLConnection.getURL();
                    String newPath = EXTERNAL_JAVA_LIBS + "/" + new File(jarURL.getFile()).getName();
                    resourceSvc.exportURL(jarURL, getTempDir(), newPath);
                }                
                
            }
        }
        getUrlList().clear();
        processCatalog(javaLibsDir, getUrlList());
        URL[] urls = getUrlList().toArray(new URL[0]);
        //ClassLoader parent = this.getClassLoader();
        this.classLoader = new URLClassLoader(urls, classLoader);
    }

    private List<URL> collectURLs(ClassLoader classLoader, List<URL> seen) {
        if (classLoader instanceof URLClassLoader) {
            URLClassLoader urlClassLoader = (URLClassLoader) classLoader;
            for (URL url: urlClassLoader.getURLs()) {
                seen.add(url);
            }
        }
        ClassLoader parent = classLoader.getParent();
        if (parent != null) {
            collectURLs(parent, seen);
        }
        return seen;
    }

    private void processCatalog(File lib, List<URL> urlList) {
        for (File file: lib.listFiles()) {
            if (file.isDirectory()) {
                processCatalog(file, urlList);
            }
            else if(file.isFile() && file.getName().toLowerCase().endsWith(".jar")) {
                try {
                    urlList.add(file.toURI().toURL());
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public File findFile(String name) {
        File javaLibsDir = new File(getTempDir(), EXTERNAL_JAVA_LIBS);
        for (File file: javaLibsDir.listFiles()) {
            if (file.isFile() && file.getName().startsWith(name)) {
                return file;
            }
        }
        return null;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public List<URL> getUrlList() {
        return urlList;
    }
}
