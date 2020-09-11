package ru.neoflex.meta.utils.vcs;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.tigris.subversion.svnclientadapter.commandline.CmdLineClientAdapterFactory;
import org.tigris.subversion.svnclientadapter.javahl.JhlClientAdapterFactory;

import java.io.File;

public class SVNFactory implements IVCSFactory {
    private final static Log logger = LogFactory.getLog(SVNFactory.class);

    @Override
    public void init() {
        try {
            JhlClientAdapterFactory.setup();
            logger.debug("JhlClientAdapterFactory factory registered");
        } catch (Throwable e) {
            logger.info("Can't register JhlClientAdapterFactory factory");
        }
        try {
            CmdLineClientAdapterFactory.setup();
            logger.debug("CmdLineClientAdapterFactory factory registered");
        } catch (Throwable e1) {
            logger.info("Can't register CmdLineClientAdapterFactory factory");
        }
    }

    @Override
    public IVCS create(File local, String userName, String password, String remote) {
        try {
            return new SVN(local, userName, password, remote);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
