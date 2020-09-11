package ru.neoflex.meta.svc;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import javax.annotation.PostConstruct;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.impl.EPackageRegistryImpl;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.teneo.PersistenceOptions;
import org.eclipse.emf.teneo.extension.ExtensionManager;
import org.eclipse.emf.teneo.hibernate.HbContext;
import org.eclipse.emf.teneo.hibernate.HbDataStore;
import org.eclipse.emf.teneo.hibernate.HbHelper;
import org.eclipse.emf.teneo.mapping.strategy.EntityNameStrategy;
import org.eclipse.emf.teneo.mapping.strategy.impl.QualifyingEntityNameStrategy;
import org.hibernate.cfg.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ru.neoflex.meta.utils.EMFResource;
import ru.neoflex.meta.utils.teneo.MHbContext;

/**
 * Created by orlov on 09.06.2015.
 */
@Service("ru.neoflex.meta.svc.TeneoSvc")
public class TeneoSvc extends BaseSvc {
    public static final String HBDS_NAME = "teneo";
    public static final String MODELS_DIR = "cim/MetaServer/pim/external/psm/emf/models";
    public static final String PACKAGES_COMMON = "cim/MetaServer/pim/external/psm/emf/packages/common";
    private final static Log logger = LogFactory.getLog(TeneoSvc.class);
    private HbDataStore hbds;

    @Autowired
    private PersistentEventsListener persistentEventsListener;

    private EPackage.Registry registry = new EPackageRegistryImpl();
    
    @PostConstruct
    void init() {
        hbds = HbHelper.INSTANCE.createRegisterDataStore(HBDS_NAME);
        hbds.getHibernateConfiguration().setInterceptor(persistentEventsListener.createInterceptor(HBDS_NAME));
        
        try {
            Properties properties = new Properties();
            properties.put(PersistenceOptions.MAP_DOCUMENT_ROOT, "true");
            
            //properties.put(PersistenceOptions.ENABLE_AUDITING, "true");
            //properties.put(PersistenceOptions.AUDITING_ENTITY_PREFIX, "AUDIT");
            
            properties.put(PersistenceOptions.AUTO_ADD_REFERENCED_EPACKAGES, "true");
            properties.put(PersistenceOptions.MAXIMUM_SQL_NAME_LENGTH, "63");
            properties.put(PersistenceOptions.SQL_NAME_ESCAPE_CHARACTER, "");
            properties.put(PersistenceOptions.CASCADE_POLICY_ON_NON_CONTAINMENT, "REFRESH");
            properties.put(PersistenceOptions.FORCE_LAZY, "true");
            properties.put(PersistenceOptions.OPTIMISTIC, "false");
            properties.put(PersistenceOptions.ALWAYS_VERSION, "false");
            //properties.put(PersistenceOptions.FETCH_ASSOCIATION_EXTRA_LAZY, "true");
            Configuration configuration = new Configuration();
            configuration.configure("/teneo.hibernate.cfg.xml");
            properties.putAll(configuration.getProperties());            
            getHbds().setDataStoreProperties(properties);
            // hbds.setPackageRegistry(EPackage.Registry.INSTANCE);
            final ExtensionManager extensionManager = getHbds().getExtensionManager();
            extensionManager.registerExtension(EntityNameStrategy.class.getName(), QualifyingEntityNameStrategy.class.getName());
            extensionManager.registerExtension(HbContext.class.getName(), MHbContext.class.getName());            //reinitialize();            
        }
        finally {
            logger.info(getHbds().getMappingXML());
        }
    }

    public Resource getTeneoResource() {
        return getTeneoResource(null);
    }

    public Resource getTeneoResource(String query) {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.setPackageRegistry(registry);
        Resource resource = EMFResource.getTeneoResource(resourceSet, getHbds().getName(), query, true);
        return resource;
    }

    public void initialize(List<EPackage> persistentPackages) {
        getHbds().setEPackages(persistentPackages.toArray(new EPackage[persistentPackages.size()]));
        getHbds().initialize();
        setRegistry(getHbds().getPackageRegistry());
        String mappingXML = getHbds().getMappingXML();
        Writer out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream("teneo.generated.hbm"), StandardCharsets.UTF_8));
            try {
                out.write(mappingXML);
            } finally {
                out.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        persistentEventsListener.registerEventListeners(hbds.getSessionFactory(), "teneo");
        getHbds().getHibernateConfiguration().setInterceptor(persistentEventsListener.createInterceptor("teneo"));
    }

    public void reinitialize(List<EPackage> persistentPackages) {
        final Resource res = getTeneoResource();
        try {
            EMFResource.generateXmiFromHutn(new File(getMSpaceDir(), MODELS_DIR), new File(getMSpaceDir(), PACKAGES_COMMON));
            EMFResource.generateXmiFromEcore(new File(getMSpaceDir(), MODELS_DIR), new File(getMSpaceDir(), PACKAGES_COMMON));
            EMFResource.loadDirContentToResource(new File(getMSpaceDir(), MODELS_DIR), res);
        }
        finally {
            try {
                res.save(null);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public HbDataStore getHbds() {
        return hbds;
    }

    public EPackage.Registry getRegistry() {
        return registry;
    }

    public void setRegistry(EPackage.Registry registry) {
        this.registry.putAll(registry);
    }
}
