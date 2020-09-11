package ru.neoflex.meta.svc;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Map;
import java.util.Properties;

/**
 * Created by orlov on 08.06.2015.
 */
@Service
public class AntSvc extends BaseSvc {
    private final static Log logger = LogFactory.getLog(EpsilonSvc.class);
    public final static String ANT_PATH = "/ant";

    static class Ant extends org.apache.tools.ant.Main {
        int result = 0;

        protected void exit(final int exitCode) {
            result = exitCode;
        }
    }

    public int run(String fileName, Map props) {
        Ant ant = new Ant();
        String[] args = new String[]{
                "-buildfile", fileName,
                "-noinput",
        };
        Properties userProperties = new Properties();
        userProperties.putAll(props);
        ant.startAnt(args, userProperties, null);
        logger.info(fileName + ": " + ant.result);
        return ant.result;
    }

    public int run(File file, Map props) {
        return run(file.getAbsolutePath(), props);
    }
}
