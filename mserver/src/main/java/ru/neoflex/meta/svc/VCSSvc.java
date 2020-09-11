package ru.neoflex.meta.svc;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Service;
import ru.neoflex.meta.utils.vcs.GITFactory;
import ru.neoflex.meta.utils.vcs.IVCS;
import ru.neoflex.meta.utils.vcs.IVCSFactory;
import ru.neoflex.meta.utils.vcs.SVNFactory;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by orlov on 10.08.2016.
 */
@Service
public class VCSSvc extends BaseSvc {
    private final static Log logger = LogFactory.getLog(VCSSvc.class);

    private Map<String, IVCSFactory> ivcsFactoryMap = new HashMap<>();

    @PostConstruct
    void init() {
        ivcsFactoryMap.put("SVN", new SVNFactory());
        ivcsFactoryMap.put("GIT", new GITFactory());
        for (IVCSFactory factory: ivcsFactoryMap.values()) {
            factory.init();
        }
    }

    public IVCS getVCS(String vcsType, File local, String userName, String password, String remote) {
        IVCSFactory factory = ivcsFactoryMap.get(vcsType);
        if (factory == null) {
            throw new RuntimeException(vcsType + ": unknown VCS");
        }
        return factory.create(local, userName, password, remote);
    }
}
