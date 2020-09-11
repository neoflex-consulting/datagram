package ru.neoflex.meta.utils.vcs;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.tigris.subversion.svnclientadapter.*;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class SVN implements IVCS {
    private final static Log logger = LogFactory.getLog(SVN.class);
    private final static SimpleDateFormat jsonTimestampFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private ISVNClientAdapter svnClient;
    private File local;
    private String remote;

    public static class NotifyListener implements ISVNNotifyListener {
        public void setCommand(int cmd) {
            // the command that is being executed. See ISVNNotifyListener.Command
            // ISVNNotifyListener.Command.ADD for example
        }
        public void logMessage(String message) {
            logger.debug(message);
        }

        public void logCommandLine(String message) {
            // the command line used
            logger.info(message);
        }

        public void logError(String message) {
            // when an error occurs
            logger.error(message);
        }

        public void logRevision(long revision, String path) {
            // when command completes against revision
            logger.info("revision :" +revision);
        }

        public void logCompleted(String message) {
            // when command completed
            logger.debug(message);
        }

        public void onNotify(File path, SVNNodeKind nodeKind) {
            // each time the status of a file or directory changes (file added, reverted ...)
            // nodeKind is SVNNodeKind.FILE or SVNNodeKind.DIR

            // this is the function we use in subclipse to know which files need to be refreshed

            logger.info("Status of "+path.toString()+" has changed");
        }
    }

    protected SVN(File local, String userName, String password, String remote) throws SVNClientException {
        String bestClientType = SVNClientAdapterFactory.getPreferredSVNClientType();
        logger.debug("Using " + bestClientType + " factory");
        svnClient = SVNClientAdapterFactory.createSVNClient(bestClientType);
        svnClient.addNotifyListener(new NotifyListener());
        if (userName != null) {
            svnClient.setUsername(userName);
            svnClient.setPassword(password);
        }
        this.local = local;
        this.remote = remote;
    }

    @Override
    public void checkout() {
        try {
            svnClient.checkout(new SVNUrl(remote), local, SVNRevision.HEAD, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void commit(String message) {
        try {
            for (ISVNStatus status: svnClient.getStatus(local, true, false)) {
                if (status.getTextStatus().toString().equals("missing")) {
                    svnClient.remove(new File[]{status.getFile()}, true);
                }
                if (status.getTextStatus().toString().equals("unversioned")) {
                    svnClient.addFile(status.getFile());
                }
            }
            svnClient.commit(new File[] {local}, message, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void commit(List<File> files, String message) {
        try {
            for (File file: files) {
                logger.info("Add file " + file.getAbsolutePath());
                if (svnClient.getInfo(file) == null) {
                    svnClient.addFile(file);
                }
            }
            svnClient.commit(new File[] {local}, message, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void update() {
        try {
            svnClient.update(local, SVNRevision.HEAD, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isVersioned() {
        try {
            ISVNInfo svnInfo = svnClient.getInfo(local);
            return svnInfo != null && svnInfo.getRevision() != SVNRevision.INVALID_REVISION;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public VCSInfo getFileInfo(File file) {
        try {
            ISVNInfo fileInfo = svnClient.getInfo(file);
            if (fileInfo != null && fileInfo.getUrl() != null) {
                VCSInfo result = new VCSInfo();
                result.setLastChangedDate(jsonTimestampFormatter.format(fileInfo.getLastChangedDate()));
                result.setLastCommitAuthor(fileInfo.getLastCommitAuthor());
                result.setLastChangedRevision(fileInfo.getLastChangedRevision().toString());
                ISVNLogMessage[] logMessages = svnClient.getLogMessages(fileInfo.getUrl(), fileInfo.getLastChangedRevision(), fileInfo.getLastChangedRevision());
                ArrayList<String> messages = new ArrayList<>();
                for (ISVNLogMessage logMessage: logMessages) {
                    messages.add(logMessage.getMessage());
                }
                result.setLogMessage(String.join("\n", messages));
                return result;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void cleanup() {
        try {
            svnClient.cleanup(local);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
