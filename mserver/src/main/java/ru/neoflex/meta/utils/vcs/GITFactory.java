package ru.neoflex.meta.utils.vcs;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;

public class GITFactory implements IVCSFactory {
    private final static Log logger = LogFactory.getLog(GITFactory.class);
    
    @Override
    public void init() {
    }

    @Override
    public IVCS create(File local, String userName, String password, String remote) {
        try {
            return new GIT(local, userName, password, remote);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
