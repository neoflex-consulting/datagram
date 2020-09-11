package ru.neoflex.meta.svc;

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.Map;

/**
 * Created by orlov on 14.04.2015.
 */
@Service
public class TemplateSvc extends BaseSvc {
    private final static Log logger = LogFactory.getLog(TemplateSvc.class);

    public String processTemplate(String templateName, Map<String, Object> params) {
        try {
            Template template = getTemplate(templateName);
            Writer writer = new StringWriter();
            template.process(params, writer);
            return writer.toString();
        } catch (Exception e) {
            logger.error(templateName, e);
            throw new RuntimeException(e);
        }

    }

    private Template getTemplate(String templateName) {
        try {
            Configuration cfg = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
            File baseDir = new File(getMSpaceDir(), "cim/MetaServer/pim/external/psm/templates");
            File templateFile = new File(baseDir, templateName);
            cfg.setDefaultEncoding("UTF-8");
            cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
            TemplateLoader templateLoader = new FileTemplateLoader(baseDir);
            cfg.setTemplateLoader(templateLoader);
            return cfg.getTemplate(templateFile.getName());
        } catch (Exception e) {
            logger.error(templateName, e);
            throw new RuntimeException(e);
        }
    }

    public void processTemplate(String templateName, String fileName, Map<String, Object> params) {
        try {
            Template template = getTemplate(templateName);
            FileOutputStream fos = new FileOutputStream(fileName);
            try {
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(fos, "UTF-8"));
                try {
                    template.process(params, out);
                }
                finally {
                    out.close();
                }
            }
            finally {
                fos.close();
            }
        } catch (Exception e) {
            logger.error(templateName, e);
            throw new RuntimeException(e);
        }
    }
}
