package ru.neoflex.meta.svc;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import ru.neoflex.meta.integration.EventCounter;
import ru.neoflex.meta.utils.Context;

import javax.annotation.PostConstruct;

/**
 * Created by orlov on 08.04.2015.
 */
@Service("ru.neoflex.meta.svc.ContextSvc")
public class ContextSvc extends BaseSvc {
    private final static Log logger = LogFactory.getLog(ContextSvc.class);
    private static final ThreadLocal<Context> tlContext = new ThreadLocal<Context>();
    @Autowired
    private Environment environment;


    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private DbAdapter dbAdapter;
    @Autowired
    private TemplateSvc templateSvc;
    @Autowired
    private ScriptSvc scriptSvc;
    @Autowired
    private PersistentEventsListener persistentEventsListener;
    @Autowired
    private TeneoSvc teneoSvc;
    @Autowired
    private EpsilonSvc epsilonSvc;
    @Autowired
    private MSpaceSvc mSpaceSvc;
    @Autowired
    EventCounter eventCounter;    
    @Autowired
    private AntSvc antSvc;
    @Autowired
    private MavenSvc mavenSvc;
    @Autowired
    private ClassLoaderSvc classLoaderSvc;
    @Autowired
    private ScalaSvc scalaSvc;
    @Autowired
    private VCSSvc vcsSvc;
    @Autowired
    private ResourceSvc resourceSvc;
    @Autowired
    private SchedulingSvc schedulingSvc;
    @Autowired
    private AppCacheSvc appCacheSvc;
    @Autowired
    private GitflowSvc gitflowSvc;

    public MavenSvc getMavenSvc() {
		return mavenSvc;
	}

	public void setMavenSvc(MavenSvc mavenSvc) {
		this.mavenSvc = mavenSvc;
	}

	public static Context getCurrent() {
        return tlContext.get();
    }

    public void inContext(Runnable instance) {
        Context context = new Context(this, tlContext.get());
        tlContext.set(context);
        try {
            try {
                try {
                    instance.run();
                }
                finally {
                    context.commitResources();
                }
            }
            catch(Exception e) {
                context.rollbackResources();
                throw e;
            }
        }
        finally {
            tlContext.set(context.getParent());
        }
    }

    public DbAdapter getDbAdapter() {
        return dbAdapter;
    }

    public void setDbAdapter(DbAdapter dbAdapter) {
        this.dbAdapter = dbAdapter;
    }


    public TemplateSvc getTemplateSvc() {
        return templateSvc;
    }

    public void setTemplateSvc(TemplateSvc templateSvc) {
        this.templateSvc = templateSvc;
    }

    public ScriptSvc getScriptSvc() {
        return scriptSvc;
    }  

    @PostConstruct
    void init() {
        inContext(new Runnable() {
            @Override
            public void run() {
                scriptSvc.runAll("init");
            }
        });
    }

    public PersistentEventsListener getPersistentEventsListener() {
        return persistentEventsListener;
    }

    public void setPersistentEventsListener(PersistentEventsListener persistentEventsListener) {
        this.persistentEventsListener = persistentEventsListener;
    }

    public TeneoSvc getTeneoSvc() {
        return teneoSvc;
    }

    public void setTeneoSvc(TeneoSvc teneoSvc) {
        this.teneoSvc = teneoSvc;
    }

    public EpsilonSvc getEpsilonSvc() {
        return epsilonSvc;
    }

    public void setEpsilonSvc(EpsilonSvc epsilonSvc) {
        this.epsilonSvc = epsilonSvc;
    }

    public MSpaceSvc getmSpaceSvc() {
        return mSpaceSvc;
    }
    
    public void onEvent(Object event) {
    	eventCounter.event();
    }

	public AntSvc getAntSvc() {
		return antSvc;
	}

	public void setAntSvc(AntSvc antSvc) {
		this.antSvc = antSvc;
	}

    public ClassLoaderSvc getClassLoaderSvc() {
        return classLoaderSvc;
    }

    public void setClassLoaderSvc(ClassLoaderSvc classLoaderSvc) {
        this.classLoaderSvc = classLoaderSvc;
    }

    public ScalaSvc getScalaSvc() {
        return scalaSvc;
    }

    public void setScalaSvc(ScalaSvc scalaSvc) {
        this.scalaSvc = scalaSvc;
    }

    public VCSSvc getVcsSvc() {
        return vcsSvc;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public ResourceSvc getResourceSvc() {
        return resourceSvc;
    }

    public SchedulingSvc getSchedulingSvc() {
        return schedulingSvc;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public AppCacheSvc getAppCacheSvc() {
        return appCacheSvc;
    }

    public void setAppCacheSvc(AppCacheSvc appCacheSvc) {
        this.appCacheSvc = appCacheSvc;
    }

    public static boolean hasAuthority(String ...authorities) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            for (GrantedAuthority grantedAuthority: authentication.getAuthorities()) {
                for (String authority: authorities) {
                    if (authority.equals(grantedAuthority.getAuthority())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public GitflowSvc getGitflowSvc() {
        return gitflowSvc;
    }
}
