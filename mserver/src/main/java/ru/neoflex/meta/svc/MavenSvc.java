package ru.neoflex.meta.svc;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.maven.shared.invoker.*;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Created by orlov on 08.06.2015.
 */
@Service
public class MavenSvc extends BaseSvc {
    private final static Log logger = LogFactory.getLog(MavenSvc.class);

    public int run(File pomFile, String goals, String home, String repository, String site, Map props) {
        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(pomFile);
        request.setInteractive(false);
        if (goals != null && goals.length() > 0) {
            List<String> goalsList = Arrays.asList(goals.split(","));
            request.setGoals(goalsList);
        }
        if (site != null && site.length() > 0) {
            request.setBaseDirectory(new File(site));
        }
        if (props != null) {
            Properties properties = new Properties();
            properties.putAll(props);
            request.setProperties(properties);
        }
        final Map<String, String> contextMap = MDC.getCopyOfContextMap();
        request.setErrorHandler(new InvocationOutputHandler() {
            @Override
            public void consumeLine(String s) {
                MDC.setContextMap(contextMap);
                logger.error(s);
            }
        });
        request.setOutputHandler(new InvocationOutputHandler() {
            @Override
            public void consumeLine(String s) {
                MDC.setContextMap(contextMap);
                logger.info(s);
            }
        });
        Invoker invoker = new DefaultInvoker();
        if (home != null && home.length() > 0) {
            invoker.setMavenHome(new File(home));
        }
        if (repository != null && repository.length() > 0) {
            invoker.setLocalRepositoryDirectory(new File(repository));
        }
        try {
            InvocationResult result = invoker.execute(request);
            if ( result.getExitCode() != 0 )
            {
                if ( result.getExecutionException() != null )
                {
                    logger.error(result.getExecutionException());
                    throw new IllegalStateException(result.getExecutionException());
                }
                else
                {
                    logger.error("Build failed. Exit code: " + result.getExitCode());
                    throw new IllegalStateException("Build failed. Exit code: " + result.getExitCode());
                }
            }
            return 0;
        } catch (MavenInvocationException e) {
            logger.error(e);
            throw new RuntimeException(e);
        }
    }
}
