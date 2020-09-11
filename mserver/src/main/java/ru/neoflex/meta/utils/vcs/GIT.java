package ru.neoflex.meta.utils.vcs;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class GIT implements IVCS {
    private final static Log logger = LogFactory.getLog(GIT.class);
    private static SimpleDateFormat jsonTimestampFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private File local;
    private CredentialsProvider credentialsProvider = null;
    private String remote;
    private ProgressMonitor progressMonitor;

    protected GIT(File local, String userName, String password, String remote) {
        if (userName != null && password != null) {
            credentialsProvider = new UsernamePasswordCredentialsProvider(userName, password);
        }
        this.local = local;
        this.remote = remote;
        this.progressMonitor = new TextProgressMonitor(new StringWriter(){
            public void flush() {
                logger.info(toString());
                getBuffer().setLength(0);
            }
        });
    }

    @Override
    public void checkout() {
        try {
            if (StringUtils.isNotEmpty(remote)) {
                logger.info("clone: " + remote + " to " + local.getAbsolutePath());
                Git
                        .cloneRepository()
                        .setCredentialsProvider(credentialsProvider)
                        .setURI(remote)
                        .setDirectory(local)
                        .setProgressMonitor(progressMonitor)
                        .call()
                        .close();
            }
            else {
                logger.info("init: " + local.getAbsolutePath());
                Git.init().setDirectory(local).call().close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Repository getRepository() throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        builder.findGitDir(local);
        builder.readEnvironment();
        builder.setMustExist(true);
        Repository repository = builder.build();
        return repository;
    }

    private String relativize(Repository repo, File file) {
        Path parent = repo.getDirectory().getParentFile().toPath();
        return parent.relativize(file.toPath()).toString();
    }

    @Override
    public void commit(String message) {
        try {
            Repository repo = getRepository();
            try {
                Git git = new Git(repo);
                Status status = git.status().call();
                for (String filepattern: status.getUntracked()) {
                    logger.info("Add untracked " + filepattern);
                    git.add().addFilepattern(filepattern).call();
                }
                for (String filepattern: status.getModified()) {
                    logger.info("Add modified " + filepattern);
                    git.add().addFilepattern(filepattern).call();
                }
                for (String filepattern: status.getMissing()) {
                    logger.info("Rm missing " + filepattern);
                    git.rm().addFilepattern(filepattern).call();
                }
                logger.info("Commit: " + message);
                git.commit().setMessage(message).call();
                push(git);
            }
            finally {
                repo.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void commit(List<File> files, String message) {
        try {
            Repository repo = getRepository();
            try {
                Git git = new Git(repo);
                for (File file: files) {
                    logger.info("Add file " + file.getAbsolutePath());
                    String filepattern = relativize(repo, file);
                    git.add().addFilepattern(filepattern).call();
                }
                logger.info("Commit: " + message);
                git.commit().setMessage(message).call();
                push(git);
            }
            finally {
                repo.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void push(Git git) throws GitAPIException {
        if (StringUtils.isNotEmpty(remote)) {
            logger.info("Push " + remote);
            Iterable<PushResult> results = git
                    .push()
                    .setRemote(remote)
                    .setCredentialsProvider(credentialsProvider)
                    .setProgressMonitor(progressMonitor)
                    .call();
            for (PushResult result: results) {
                for (RemoteRefUpdate update: result.getRemoteUpdates()) {
                    RemoteRefUpdate.Status status = update.getStatus();
                    if (status != RemoteRefUpdate.Status.OK && status != RemoteRefUpdate.Status.UP_TO_DATE) {
                        String msg = status.toString();
                        if (update.getMessage() != null) {
                            msg = msg + ": " + update.getMessage();
                        }
                        throw new RuntimeException(msg);
                    }
                }
            }
        }
    }

    @Override
    public void update() {
        try {
            Repository repo = getRepository();
            try {
                Git git = new Git(repo);
                git.checkout().setAllPaths(true).setProgressMonitor(progressMonitor).call();
                if (StringUtils.isNotEmpty(remote)) {
                    logger.info("Pull " + remote);
                    StoredConfig config = repo.getConfig();
                    config.setString("remote", "origin", "url", remote);
                    git
                            .pull()
                            .setRemote("origin")
                            .setStrategy(MergeStrategy.THEIRS)
                            .setCredentialsProvider(credentialsProvider)
                            .setProgressMonitor(progressMonitor)
                            .call();
                }
            }
            finally {
                repo.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isVersioned() {
        try {
            Repository repo = getRepository();
            repo.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public VCSInfo getFileInfo(File file) {
        try {
            Repository repo = getRepository();
            try {
                Git git = new Git(repo);
                String filepattern = relativize(repo, file);
                Iterable<RevCommit> revCommits = git.log().addPath(filepattern).setMaxCount(1).call();
                for (RevCommit commit: revCommits) {
                    PersonIdent committer = commit.getCommitterIdent();
                    VCSInfo vcsInfo = new VCSInfo();
                    vcsInfo.setLogMessage(commit.getFullMessage());
                    vcsInfo.setLastChangedDate(jsonTimestampFormatter.format(new Date(commit.getCommitTime()*1000L)));
                    vcsInfo.setLastCommitAuthor(committer.getName());
                    vcsInfo.setLastChangedRevision(commit.getName());
                    return vcsInfo;
                }
                return null;
            }
            finally {
                repo.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void cleanup() {
        try {
            Repository repo = getRepository();
            try {
                Git git = new Git(repo);
                logger.info("gc: " + repo.getDirectory().getAbsolutePath());
                git.gc().setAggressive(true).setProgressMonitor(progressMonitor).call();
            }
            finally {
                repo.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
