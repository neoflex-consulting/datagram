package ru.neoflex.meta.svc;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.tool.hbm2ddl.SchemaUpdate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.w3c.dom.*;
import ru.neoflex.meta.utils.MetaHelper;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.ParserConfigurationException;
import java.util.List;

/**
 * Created by orlov on 02.04.2015.
 */
@Service
public class DbAdapter extends BaseSvc {
    @Autowired
    private PersistentEventsListener persistentEventsListener;
    @Autowired
    private TeneoSvc teneoSvc;

    public DbAdapter() {
    }

    @PostConstruct
    void init() {
        //refreshDataSessionFactory();
    }

    public Configuration getConfiguration(String resource) {
        Configuration configuration = new Configuration();
        configuration.configure(resource);
        return configuration;
    }

    public ServiceRegistry getServiceRegistry (Configuration configuration) {
        return new StandardServiceRegistryBuilder()
                .applySettings(configuration.getProperties())
                .build();
    }

    public SessionFactory getSessionFactory(String dbtype) {
        if ("teneo".equals(dbtype)) {
            return getTeneoSessionFactory();
        }
        return null;
    }

    private SessionFactory getTeneoSessionFactory() {    	
        return teneoSvc.getHbds().getSessionFactory();
    }

    public PersistentEventsListener getPersistentEventsListener() {
        return persistentEventsListener;
    }

    public void setPersistentEventsListener(PersistentEventsListener persistentEventsListener) {
        this.persistentEventsListener = persistentEventsListener;
    }
}
