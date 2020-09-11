package ru.neoflex.meta.svc;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Service;
import ru.neoflex.meta.utils.Context;
import ru.neoflex.meta.utils.MetaResource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

/**
 * Created by orlov on 26.08.2016.
 */
@Service
public class ResourceSvc {
    private final static Log logger = LogFactory.getLog(ContextSvc.class);
    @Autowired
    private ResourceLoader resourceLoader;

    public Resource[] loadResources(String pattern) {
        try {
            return ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources(pattern);
        } catch (IOException e) {
            logger.warn(e.getMessage());
            return new Resource[0];
        }
    }

    public Resource[] loadClasspathResources(String pattern) {
        return loadResources("classpath:/" + pattern);
    }

    public File exportDir(String path, File to) {
        Resource[] resources = loadClasspathResources(path + "/**");
        for (Resource resource: resources) {
            try {
                String resourcePath = null;
                URL url = resource.getURL();
                String urlString = url.toString();
                if (!urlString.endsWith("/")) {
                    int index = urlString.lastIndexOf(path);
                    if (index >= 0) {
                        resourcePath = urlString.substring(index + path.length() + 1);
                    }
                    if (resourcePath != null) {
                        exportURL(url, to, resourcePath);
                    }
                }
            }
            catch (IOException e) {
                logger.warn(e.getMessage());
            }
        }
        return to;
    }

    public File export(String path, File to, String newPath) {
        try {
            URL in = MetaResource.getURL(path, true);
            if (in == null) {
                return null;
            }
            return exportURL(in, to, newPath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public File exportURL(URL in, File to, String newPath) {
        try {
            File outFile = new File(to, newPath);
            if (in.sameFile(outFile.toURI().toURL())) {
                return outFile;
            }
            if (outFile.exists() && outFile.isFile()) {
                File inFile = new File(in.getFile());
                if (inFile.exists() && inFile.isFile() && inFile.lastModified() <= outFile.lastModified()) {
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

}
