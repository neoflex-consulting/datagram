package ru.neoflex.meta.utils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import ru.neoflex.meta.svc.BaseSvc;

import java.io.*;
import java.net.*;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Created by orlov on 19.05.2016.
 */
public class MetaResource {
    private final static Log logger = LogFactory.getLog(MetaResource.class);

    public static URL getURL(String path){
        return getURL(path, false);
    }
    public static URL getURL(String path, boolean safe){
        try {
            return (new ClassPathResource(path)).getURL();
        } catch (Exception e) {
            if (safe) {
                return null;
            }
            throw new RuntimeException(e);
        }
    }
    public static URI getURI(String path){
        try {
            return getURL(path).toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
    public static URI getURISafe(String path){
        try {
            return getURI(path);
        } catch (Throwable e) {
            return null;
        }
    }
    public static String parentDirPath(String path){
        int i = path.lastIndexOf("/");
        if (i < 0)
            return "";
        return path.substring(0, i);
    }
    public static String parentDirPath(URI pathURI){
        return parentDirPath(pathURI.toString());
    }

    public static File export(String path, File to, String newPath) {
        return export(path, to, newPath, false);
    }

    public static File export(String path, File to, String newPath, boolean fromJarOnly) {
        try {
            URL in = getURL(path, true);
            if (in == null) {
                return null;
            }
            return exportURL(in, to, newPath, fromJarOnly);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static File exportURL(URL in, File to, String newPath) {
        return exportURL(in, to, newPath, false);
    }


    public static File exportURL(URL in, File to, String newPath, boolean fromJarOnly) {
        try {
            File outFile = new File(to, newPath);
            if (in.sameFile(outFile.toURI().toURL())) {
                return outFile;
            }
            File inFile = new File(in.getFile());
            if (fromJarOnly && inFile.exists() && inFile.isFile()) {
                return inFile;
            }
            if (outFile.exists() && outFile.isFile()) {
                if (!inFile.exists() || inFile.exists() && inFile.isFile() && inFile.lastModified() <= outFile.lastModified()) {
                    return outFile;
                }
            }
            outFile.getParentFile().mkdirs();
            InputStream is = in.openStream();
            try {
                FileOutputStream os = new FileOutputStream(outFile);
                try {
                    ReadableByteChannel rbc = Channels.newChannel(is);
                    try {
                        os.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                    }
                    finally {
                        rbc.close();
                    }
                }
                finally {
                    os.close();
                }
            }
            finally {
                is.close();
            }
            return outFile;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static File exportDir(String path, File to) {
        return Context.getCurrent().getContextSvc().getResourceSvc().exportDir(path, to);
    }
}
