package ru.neoflex.meta.svc;

import fmpp.progresslisteners.ConsoleProgressListener;
import fmpp.progresslisteners.StatisticsProgressListener;
import fmpp.setting.SettingException;
import fmpp.setting.Settings;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by orlov on 16.05.2015.
 */
@Service
public class FMPPSvc extends BaseSvc {
    public Map execute(String scriptName, Map<String, Object> props) {
        try {
            File configDir = new File(System.getProperty("config.dir", System.getProperty("user.dir")));
            File scriptFile = new File(configDir, scriptName);
            File baseDir = scriptFile.getParentFile();
            Settings settings = new Settings(baseDir);
            settings.addProgressListener(new ConsoleProgressListener());
            StatisticsProgressListener stats = new StatisticsProgressListener();
            settings.addProgressListener(stats);
            settings.loadDefaults(baseDir);
            for (String key: props.keySet()) {
                settings.setEngineAttribute(key, props.get(key));
            }
            settings.execute();
            Map result = new HashMap();
            result.put("Copied", stats.getCopied());
            result.put("Accessed", stats.getAccessed());
            result.put("Executed", stats.getExecuted());
            result.put("Failed", stats.getFailed());
            result.put("Rendered", stats.getXmlRendered());
            result.put("Warnings", stats.getWarnings());
            result.put("ProcessingTime", stats.getProcessingTime());
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
