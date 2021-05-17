package ru.neoflex.meta.svc;

import com.beijunyi.parallelgit.filesystem.Gfs;
import com.beijunyi.parallelgit.filesystem.GitFileSystem;
import com.beijunyi.parallelgit.filesystem.GitPath;
import com.beijunyi.parallelgit.filesystem.commands.GfsCommit;
import com.beijunyi.parallelgit.filesystem.commands.GfsMerge;
import com.beijunyi.parallelgit.filesystem.merge.MergeConflict;
import com.beijunyi.parallelgit.utils.BranchUtils;
import com.beijunyi.parallelgit.utils.RepositoryUtils;
import com.beijunyi.parallelgit.utils.exceptions.RefUpdateLockFailureException;
import com.beijunyi.parallelgit.utils.exceptions.RefUpdateRejectedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.marschall.pathclassloader.PathClassLoader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.merge.MergeFormatter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.hibernate.Session;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.tool.hbm2ddl.SchemaUpdate;
import org.postgresql.core.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ru.neoflex.meta.emfgit.GitURLStreamHandler;
import ru.neoflex.meta.utils.Context;
import ru.neoflex.meta.utils.ECoreUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.*;
import java.util.stream.Collectors;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.eclipse.jgit.lib.Constants.DOT_GIT;
import static org.eclipse.jgit.lib.Constants.*;
import static ru.neoflex.meta.utils.ECoreUtils.*;
import static ru.neoflex.meta.utils.EMFResource.createResourceSet;

@Service("ru.neoflex.meta.svc.GitflowSvc")
public class GitflowSvc extends BaseSvc {
    private final static Log logger = LogFactory.getLog(GitflowSvc.class);
    public static final String BRANCH = "branch";
    private static final ThreadLocal<String> tlCurrentBranch = new ThreadLocal<String>();
    public static final String PARENT = "parent";
    public static final String SOURCES = "sources";
    public static final String BUILD = "build";
    public static final String SYNC_TAG = "db";

    {
        try {
            GitURLStreamHandler.registerFactory();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Value("${teneo.url}")
    String teneoURL;
    @Value("${gitflow.root:${user.dir}/gitflow}")
    String gitflowRoot;
    @Autowired
    TeneoSvc teneoSvc;
    @Autowired
    ContextSvc contextSvc;
    private final ObjectMapper mapper = new ObjectMapper();
    @Value("${gitflow.repoName:${cust.code:default}}")
    private String repoName;
    private Repository repository;
    private static final ThreadLocal<GitFileSystem> tlGfs = new ThreadLocal<>();

    public static void setCurrentGfs(GitFileSystem gfs) {
        tlGfs.set(gfs);
    }

    public static GitFileSystem getCurrentGfs() {
        return tlGfs.get();
    }

    private final ProgressMonitor progressMonitor = new TextProgressMonitor(new StringWriter() {
        public void flush() {
            logger.info(toString());
            getBuffer().setLength(0);
        }
    });

    @PostConstruct
    private void init() throws GitAPIException, IOException {
        File repoDir = new File(gitflowRoot, repoName);
        repoDir.mkdirs();
        repository = new File(repoDir, DOT_GIT).exists() ?
                RepositoryUtils.openRepository(repoDir, false) :
                RepositoryUtils.createRepository(repoDir, false);
        if (BranchUtils.getBranches(repository).size() == 0) {
            try (Git git = new Git(repository)) {
                git.commit().setMessage("Initial commit").setAllowEmpty(true).call();
            }
        }
        for (String branch : BranchUtils.getBranches(repository).keySet()) {
            if (!MASTER.equals(branch)) {
                setCurrentBranch(branch);
                updateScheme();
            }
        }
        setCurrentBranch(MASTER);
    }

    @PreDestroy
    private void fini() {
        repository.close();
    }

    private String relativize(Repository repo, File file) {
        Path parent = repo.getDirectory().getParentFile().toPath();
        return parent.relativize(file.toPath()).toString().replace('\\', '/');
    }

    private Repository getRepository() throws IOException {
        return repository;
    }

    private String getParentBranch(String branch) throws IOException, GitAPIException {
//        try (RevWalk walk = new RevWalk(repository)) {
//            Ref branchRef = repository.findRef(branch);
//            ObjectId branchObjectID = branchRef != null ? getActualRefObjectId(repository, branchRef) : ObjectId.fromString(branch);
//            RevCommit head = walk.parseCommit(branchObjectID);
//            while (head != null) {
//                RevCommit[] parents = head.getParents();
//                if (parents != null && parents.length > 0) {
//                    head = walk.parseCommit(parents[0]);
//                    for (Ref parentRef : findBranchesReachableFrom(head, walk, repository.getRefDatabase().getRefsByPrefix("refs/heads"))) {
//                        try (Git git = new Git(repository)) {
//                            Map<ObjectId, String> map = git
//                                    .nameRev()
//                                    .addPrefix("refs/heads")
//                                    .add(parentRef.getObjectId())
//                                    .call();
//                            for (String parent : map.values()) {
//                                if (!parent.equals(branch)) {
//                                    return parent;
//                                }
//                            }
//                        }
//                    }
//                } else {
//                    break;
//                }
//            }
//        }
        return MASTER;
    }

    public JsonNode getBranchInfo() throws IOException, GitAPIException {
        ObjectNode branchInfo = mapper.createObjectNode();
        branchInfo.put("current", GitflowSvc.getCurrentBranch());
        ObjectNode branches = branchInfo.with("branches");
        for (String branch : BranchUtils.getBranches(repository).keySet()) {
            branches.set(branch, getBranchInfo(branch));
        }
        return branchInfo;
    }

    public JsonNode getBranchInfo(String branch) throws IOException, GitAPIException {
        ObjectNode info = mapper.createObjectNode();
        info.put("parent", getParentBranch(branch));
        return info;
    }

    private void checkBranch(String branch) throws IOException {
        if (!BranchUtils.getBranches(repository).containsKey(branch)) {
            throw new RuntimeException("Branch " + branch + " does not exists");
        }
    }

    public void deleteBranch(String branch, String username, String password) throws IOException, GitAPIException {
        checkBranch(branch);
        if (MASTER.equals(branch)) {
            throw new RuntimeException("Cant delete " + MASTER);
        }
        String currentBranch = getCurrentBranch();
        String parentBranch = getParentBranch(branch);
        BranchUtils.deleteBranch(branch, repository);
        RefSpec refSpec = new RefSpec()
                .setSource(null)
                .setDestination("refs/heads/" + branch);
        Git git = new Git(repository);
        PushCommand pushCommand = git.push().setRefSpecs(refSpec);
        pushCommand.setRemote("origin");
        if (StringUtils.isNotEmpty(username) && StringUtils.isNotEmpty(password)) {
            CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(username, password);
            pushCommand.setCredentialsProvider(credentialsProvider);
        }
        pushCommand.call();
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                dropSchema(getSchema(branch));
            }
        });
        if (currentBranch.equals(branch)) {
            setCurrentBranch(parentBranch);
        } else {
            setCurrentBranch(currentBranch);
        }
    }

    public void createBranch(String branch) throws IOException, GitAPIException {
        if (BranchUtils.branchExists(branch, repository)) {
            throw new IOException("Branch " + branch + " already exists");
        }
        String currentBranch = getCurrentBranch();
        Ref ref = BranchUtils.getBranches(repository).get(currentBranch);
        BranchUtils.createBranch(branch, ref, repository);
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                initSchema(getSchema(branch), getSchema(currentBranch));
                setCurrentBranch(branch);
                logger.info(String.format("Updating new scheme"));
                updateScheme();
                try {
                    copySequences(getSchema(branch), getSchema(currentBranch));
                } catch (Throwable e) {
                    logger.error(e.getMessage());
                }
            }
        });
        tagCurrentAsSynced();
    }

    private void branchInit(String branch, File branchDir, Git git) throws IOException, GitAPIException {
        File branchInfo = new File(branchDir, "branches/" + branch);
        branchInfo.getParentFile().mkdirs();
        String doc = mapper.writeValueAsString(mapper.createObjectNode()
                .put("created", (new Date()).toString())
                .put(PARENT, getCurrentBranch())
        );
        Files.write(branchInfo.toPath(), doc.getBytes());
        git.add().addFilepattern(relativize(git.getRepository(), branchInfo)).call();
        File gitIgnore = new File(branchDir, ".gitignore");
        if (!gitIgnore.exists()) {
            Files.write(gitIgnore.toPath(), "target/\n".getBytes());
            Files.write(gitIgnore.toPath(), "lib/\n".getBytes());
            git.add().addFilepattern(relativize(git.getRepository(), gitIgnore)).call();
        }
        git.commit().setMessage("Branch " + branch + " created").call();
        setCurrentBranch(branch);
    }

    public static String getSchema(String branch) {
        String schema = branch == null || branch.length() == 0 || branch.equals(MASTER) ? "public" : branch;
        StringBuilder sb = new StringBuilder();
        try {
            Utils.escapeLiteral(sb, schema, true);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return sb.toString().toLowerCase().replaceAll("[^a-z0-9_]", "_");
    }

    public static String getCurrentBranch() {
        String branch = null;
        HttpSession session = null;
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attr != null) {
            session = attr.getRequest().getSession(false);
        }
        if (session != null) {
            branch = (String) session.getAttribute(BRANCH);
        }
        if (branch == null) {
            branch = tlCurrentBranch.get();
        }
        if (branch == null) {
            branch = MASTER;
        }
        if (session != null) {
            session.setAttribute(BRANCH, branch);
        }
        tlCurrentBranch.set(branch);
        return branch;
    }

    public void setCurrentBranch(String branch) {
        try {
            if (!BranchUtils.getBranches(repository).containsKey(branch)) {
                logger.error("Branch " + branch + " does not exists");
                branch = MASTER;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attr != null) {
            HttpSession session = attr.getRequest().getSession(false);
            if (session != null) {
                session.setAttribute(BRANCH, branch);
            }
        }
        tlCurrentBranch.set(branch);
    }

    public void updateScheme() {
        Configuration configuration = teneoSvc.getHbds().getHibernateConfiguration();
        configuration.getProperties().setProperty(Environment.DEFAULT_SCHEMA, getSchema(getCurrentBranch()));
        SchemaUpdate schemaUpdate = new SchemaUpdate(configuration);
        schemaUpdate.execute(true, true);
    }

    public void createSchema(String name) {
        Session session = Context.getCurrent().getTxSession();
        dropSchema(name);
        session.createSQLQuery(String.format("CREATE SCHEMA %s", name)).executeUpdate();
        Context.getCurrent().savepoint();
    }

    public void dropSchema(String name) {
        Session session = Context.getCurrent().getTxSession();
        session.createSQLQuery(String.format("DROP SCHEMA IF EXISTS %s CASCADE", name)).executeUpdate();
        Context.getCurrent().savepoint();
    }

    private List<String> getTables(String schema) {
        String query = "SELECT quote_ident(tablename) FROM pg_tables WHERE schemaname='%s'";
        Session session = Context.getCurrent().getSession();
        List<String> result = session.createSQLQuery(String.format(query, schema)).list();
        return result;
    }

    private void createTable(String tablename, String schemaname, String schemafrom) {
        String query = "CREATE TABLE %s.%s (LIKE %s.%s INCLUDING CONSTRAINTS INCLUDING INDEXES INCLUDING DEFAULTS)";
        Session session = Context.getCurrent().getSession();
        session.createSQLQuery(String.format(query, schemaname, tablename, schemafrom, tablename)).executeUpdate();
    }

    private void copyTable(String tablename, String schemaname, String schemafrom) {
        String query = "INSERT INTO %s.%s (SELECT * FROM %s.%s)";
        Session session = Context.getCurrent().getSession();
        session.createSQLQuery(String.format(query, schemaname, tablename, schemafrom, tablename)).executeUpdate();
    }

    public void initSchema(String name, String template) {
        createSchema(name);
        for (String tablename : getTables(template)) {
            logger.info(String.format("Copy table %s", tablename));
            createTable(tablename, name, template);
            copyTable(tablename, name, template);
        }
        Context.getCurrent().savepoint();
    }

    public void copySequences(String name, String template) {
        Session session = Context.getCurrent().getSession();
        String querySeqs = "select quote_ident(sequence_name) from information_schema.sequences where sequence_schema = '%s'";
        List<String> seqs = session.createSQLQuery(String.format(querySeqs, name)).list();
        for (String seq : seqs) {
            String queryLast = "select last_value from %s.%s";
            BigInteger lastValue = (BigInteger) session.createSQLQuery(String.format(queryLast, template, seq)).uniqueResult();
            logger.info(String.format("Set sequence %s value %d", seq, lastValue));
            String setLast = "select pg_catalog.setval('%s.%s', %d, true)";
            session.createSQLQuery(String.format(setLast, name, seq, lastValue)).list();
        }
        Context.getCurrent().savepoint();
    }

    public PersonIdent getPersonIdent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            String author = authentication.getName();
            if (author != null) {
                return new PersonIdent(author, "");
            }
        }
        return null;
    }

    public void commit(GitFileSystem gfs, String message) throws IOException {
        GfsCommit commit = Gfs.commit(gfs).message(message);
        PersonIdent authorId = getPersonIdent();
        if (authorId != null) {
            commit.author(authorId);
        }
        commit.execute();
    }

    public List<Path> exportEObject(EObject eObject, String svnCommitMessage) throws Exception {
        List<Path> result = new ArrayList<>();
        EClass eClass = eObject.eClass();
        String dir = "/model/" + eClass.getEPackage().getNsPrefix() + "/" + eClass.getName();
        return inDir(dir, svnCommitMessage == null ? "Export EObject: " + eObject : svnCommitMessage, path -> {
            ResourceSet resourceSet = createResourceSet();
            try {
                result.addAll(ECoreUtils.exportEObject(path, eObject, resourceSet));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, (src, dst) -> {
            for (Path file : result) {
                try {
                    Path dstFile = dst.resolve(src.relativize(file).toString().replace('\\', '/'));
                    Files.createDirectories(dstFile.getParent());
                    Files.copy(file, dstFile, REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return result;
        });
    }

    public void inDir(String dir, String message, Consumer<Path> processDir) throws Exception {
        inDir(dir, message, processDir, GitflowSvc::copyContentRecursive);
    }

    public static Void copyContentRecursive(Path src, Path dest) {
        copyContentRecursive(src, dest, true);
        return null;
    }

    public static void copyContentRecursive(Path src, Path dest, boolean clear) {
        try {
            if (clear) {
                deleteDirectoryRecursive(dest);
            }
            Files.createDirectories(dest);
            Files.walk(src).filter(Files::isRegularFile).forEach(source -> {
                try {
                    Path target = dest.resolve(src.relativize(source).toString().replace('\\', '/'));
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public <T> T inDir(String dir, String message, BiFunction<Path, Path, T> transact) throws Exception {
        return inDir(dir, message, null, transact);
    }

    public <T> T inDir(String dir, String message, Consumer<Path> pre, BiFunction<Path, Path, T> transact) throws Exception {
        Path temp = Files.createTempDirectory("dg");
        try {
            if (pre != null) {
                pre.accept(temp);
            }
            return inGitTransaction(message, () -> {
                return transact.apply(temp, getCurrentGfs().getRootPath().resolve(dir));
            });
        } finally {
            deleteDirectoryRecursive(temp);
        }
    }

    public <T> T inCopy(String dir, String message, Function<File, T> process) throws Exception {
        Path temp = Files.createTempDirectory("dg");
        try {
            inGitTransaction(null, () -> {
                Path gfsDir = getCurrentGfs().getRootPath().resolve(dir);
                Files.createDirectories(gfsDir);
                copyContentRecursive(gfsDir, temp, false);
                return null;
            });
            return inGitTransaction(message, () -> {
                T result = process.apply(temp.toFile());
                if (message != null) {
                    Path gfsDir = getCurrentGfs().getRootPath().resolve(dir);
                    copyContentRecursive(temp, gfsDir, true);
                }
                return result;
            });
        } finally {
            deleteDirectoryRecursive(temp);
        }
    }

    public static void deleteDirectoryRecursive(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walkFileTree(path,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                            Files.delete(dir);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }
                    }
            );
        }
    }

    public <T> T inGitTransaction(String message, Callable<T> f) throws Exception {
        return inGitTransaction(getCurrentBranch(), message, f);
    }

    public <T> T inGitTransaction(String branch, String message, Callable<T> f) throws Exception {
        int delay = 1;
        int maxDelay = 1000;
        int maxAttempts = 10;
        int attempt = 1;
        while (true) {
            try {
                GitFileSystem old = getCurrentGfs();
                GitFileSystem gfs = Gfs.newFileSystem(branch, repository);
                setCurrentGfs(gfs);
                try {
                    ClassLoader parent = Thread.currentThread().getContextClassLoader();
                    ClassLoader classLoader = new PathClassLoader(gfs.getRootPath(), parent);
                    Thread.currentThread().setContextClassLoader(classLoader);
                    try {
                        T result = f.call();
                        if (message != null) {
                            commit(gfs, message);
                        }
                        return result;
                    } finally {
                        Thread.currentThread().setContextClassLoader(parent);
                    }
                } finally {
                    setCurrentGfs(old);
                }
            } catch (RefUpdateLockFailureException | RefUpdateRejectedException e) {
                String emessage = e.getClass().getSimpleName() + ": " + e.getMessage() + " attempt no " + attempt;
                logger.debug(emessage);
                if (++attempt > maxAttempts) {
                    throw e;
                }
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ex) {
                }
                if (delay < maxDelay) {
                    delay *= 2;
                }
                continue;
            }
        }
    }

    public List<Path> exportCurrentBranch() throws Exception {
        String branch = getCurrentBranch();
        String message = "Export of " + branch + ". " + new Date();
        logger.info(message);
        List<Path> result = new ArrayList<>();
        inDir("/model", message, modelPath -> {
            contextSvc.inContext(() -> {
                try {
                    ResourceSet resourceSet = createResourceSet();
                    for (EClass eClass : topLevelClasses()) {
                        Path packagePath = modelPath.resolve(eClass.getEPackage().getNsPrefix());
                        Path classPath = packagePath.resolve(eClass.getName());
                        for (EObject eObject : allEObjects(eClass)) {
                            eObject = (EObject) ECoreUtils.prefetchEntity(getTypeName(eObject.eClass()), ((Map) eObject).get("e_id"), eObject.eClass());
                            result.addAll(ECoreUtils.exportEObject(classPath, eObject, resourceSet));
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        });
        tagCurrentAsSynced();
        return result;
    }

    public void truncateCurrentSchema() {
        String preSQL = "SELECT 'TRUNCATE TABLE ' || array_to_string(array_agg(quote_ident(schemaname) || '.' || quote_ident(tablename)), ', ') || ' CASCADE' FROM   pg_tables WHERE schemaname = '%s'";
        String query = String.format(preSQL, getSchema(getCurrentBranch()));
        List<String> result = Context.getCurrent().getSession().createSQLQuery(query).list();
        String truncate = result.get(0);
        Context.getCurrent().getTxSession().createSQLQuery(truncate).executeUpdate();
    }

    public List<Path> importCurrentBranch() throws Exception {
        return importCurrentBranch(false);
    }

    public List<Path> importCurrentBranch(boolean truncate) throws Exception {
        String branch = getCurrentBranch();
        String message = "Import of " + branch + ". " + new Date();
        logger.info(message);
        List<Path> result = new ArrayList<>();
        inGitTransaction(branch, null, () -> {
            contextSvc.inContext(new Runnable() {
                @Override
                public void run() {
                    if (truncate) {
                        truncateCurrentSchema();
                    }
                    Path modelPath = getCurrentGfs().getPath("/model");
                    try {
                        Files.createDirectories(modelPath);
                        importXmiDir(modelPath, result);
                        importRefDir(modelPath, result);
                    } catch (IOException e) {
                        throw new IllegalArgumentException(e);
                    }
                }
            });
            return null;
        });
        tagCurrentAsSynced();
        return result;
    }

    public List<Path> importCurrentBranchRefs() throws Exception {
        String branch = getCurrentBranch();
        String message = "Import of " + branch + " refs. " + new Date();
        logger.info(message);
        List<Path> result = new ArrayList<>();
        inGitTransaction(branch, null, () -> {
            contextSvc.inContext(new Runnable() {
                @Override
                public void run() {
                    Path modelPath = getCurrentGfs().getPath("model");
                    try {
                        importRefDir(modelPath, result);
                    } catch (IOException e) {
                        throw new IllegalArgumentException(e);
                    }
                }
            });
            return null;
        });
        return result;
    }

    public void importXmiDir(Path modelDir, List<Path> result) throws IOException {
        List<Path> xmiFiles = Files.walk(modelDir).filter(Files::isRegularFile).filter(file -> file.getFileName().toString().endsWith("xmi")).collect(Collectors.toList());
        for (Path file : xmiFiles) {
            logger.info("Process " + file.toString());
            importXmiPath(file);
            result.add(file);
        }
    }

    public EObject importXmiPath(Path file) {
        try {
            EObject eObject = ECoreUtils.importEObject(file);
            if (eObject != null) {
                Context.getCurrent().getTxSession().save(eObject);
                //Context.getCurrent().savepoint();
                Context.getCurrent().commitResources();
            }
            return eObject;
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public void importRefDir(Path modelDir, List<Path> result) throws IOException {
        List<Path> refsPaths = Files.walk(modelDir).filter(Files::isRegularFile).filter(file -> file.getFileName().toString().endsWith("ref")).collect(Collectors.toList());
        for (Path refsPath : refsPaths) {
            logger.info("Process " + refsPath.toString());
            if (importRefPath(refsPath) != null) {
                result.add(refsPath);
            }
        }
    }

    public EObject importRefPath(Path file) {
        try {
            JsonNode jsonNode = mapper.readTree(Files.readAllBytes(file));
            EObject eObject = treeToObjectWithRefs(jsonNode);
            if (eObject != null) {
                Context.getCurrent().getTxSession().save(eObject);
                Context.getCurrent().savepoint();
            }
            return eObject;
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }


    public void push(String remote, String username, String password) throws IOException, GitAPIException {
        Git git = new Git(repository);
        PushCommand command = git.push().setProgressMonitor(progressMonitor);
        if (StringUtils.isNotEmpty(remote)) {
            command.setRemote(remote);
        }
        if (StringUtils.isNotEmpty(username) && StringUtils.isNotEmpty(password)) {
            CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(username, password);
            command.setCredentialsProvider(credentialsProvider);
        }
        Iterable<PushResult> results = command.setPushAll().call();
        checkPushResult(results);
    }

    private void checkPushResult(Iterable<PushResult> results) {
        results:
        for (PushResult result : results) {
            updates:
            for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                RemoteRefUpdate.Status status = update.getStatus();
                if (status != RemoteRefUpdate.Status.OK && status != RemoteRefUpdate.Status.UP_TO_DATE) {
                    if (status == RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD) {
                        if (!update.getRemoteName().contains(getCurrentBranch())) {
                            logger.warn("Local copy of remote " + update.getRemoteName() + " need to be updated");
                            continue updates;
                        }
                    }
                    String msg = status.toString();
                    if (update.getMessage() != null) {
                        msg = msg + ": " + update.getMessage();
                    }
                    throw new RuntimeException(msg);
                }
            }
        }
    }

    public void pull(String remoteBranchName, String remote, String username, String password) throws IOException, GitAPIException, URISyntaxException {
        Git git = new Git(repository);
        PullCommand command = git.pull().setProgressMonitor(progressMonitor);
        if (StringUtils.isNotEmpty(remote)) {
            StoredConfig config = repository.getConfig();
            config.setString("remote", "origin", "url", remote);
            RemoteConfig originConfig = new RemoteConfig(config, "origin");
            originConfig.addFetchRefSpec(new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
            originConfig.update(config);
            config.save();
        }
        command.setRemote("origin");
        if (StringUtils.isNotEmpty(remoteBranchName)) {
            command.setRemoteBranchName(remoteBranchName);
        }
        if (StringUtils.isNotEmpty(username) && StringUtils.isNotEmpty(password)) {
            CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(username, password);
            command.setCredentialsProvider(credentialsProvider);
        }
        command.call();
    }

    public void merge(String from, String to) throws Exception {
        setCurrentBranch(to);
        GfsMerge merge = Gfs.merge(Gfs.newFileSystem(to, repository)).source(from);
        PersonIdent authorId = getPersonIdent();
        if (authorId != null) {
            merge.committer(authorId);
        }
        GfsMerge.Result result = merge.execute();
        if (result.isSuccessful()) {
            syncCurrentBranch();
        } else {
            String msg = result.getStatus().toString();
            for (String file : result.getConflicts().keySet()) {
                MergeConflict conflict = result.getConflicts().get(file);
                msg = msg + String.format("\n%s: %s", file, new String(conflict.format(new MergeFormatter()), StandardCharsets.UTF_8));
            }
            throw new IllegalArgumentException(msg);
        }
    }

    public void syncCurrentBranch() {
        contextSvc.inContext(() -> {
            try {
                inGitTransaction("Sync Current Branch", () -> {
                    String syncRef = R_TAGS + SYNC_TAG + "-" + getCurrentBranch();
                    ObjectId revId = getActualRefObjectId(repository, repository.findRef(syncRef));
                    if (revId == null) {
                        importCurrentBranch();
                    } else {
                        for (String path : changedFiles(repository, syncRef, "HEAD")) {
                            if (path.startsWith("model/")) {
                                Path file = getCurrentGfs().getPath(path);
                                if (Files.isRegularFile(file)) {
                                    if (path.endsWith(".xmi")) {
                                        logger.info(file.toString());
                                        importXmiPath(file);
                                    } else if (path.endsWith(".ref")) {
                                        logger.info(file.toString());
                                        importRefPath(file);
                                    }
                                }
                            }
                        }
                    }
                    tagCurrentAsSynced();
                    return null;
                });
            } catch (Throwable th) {
                throw new RuntimeException(th);
            }
        });
    }

    public void tagCurrentAsSynced() throws IOException, GitAPIException {
        tagRepo(SYNC_TAG + "-" + getCurrentBranch());
    }

    public void tagRepo(String tag) throws IOException, GitAPIException {
        try (Git git = new Git(repository)) {
            git.tag().setName(tag).setForceUpdate(true).setAnnotated(false).call();
        } catch (JGitInternalException e) {
            logger.warn("tagRepo " + tag, e);
        }
    }

    public ArrayNode changes(String from, String to) throws IOException, GitAPIException {
        ArrayNode result = mapper.createArrayNode();
        for (String path : changedFiles(repository, from, to)) {
            result.add(path);
        }
        return result;
    }

    public Iterable<RevCommit> getJGitLogBetween(final String rev1, final String rev2) throws IOException, GitAPIException {
        Ref refFrom = repository.findRef(rev1);
        ObjectId fromObjectID = refFrom != null ? getActualRefObjectId(repository, refFrom) : ObjectId.fromString(rev1);
        Ref refTo = repository.findRef(rev2);
        ObjectId toObjectID = refTo != null ? getActualRefObjectId(repository, refTo) : ObjectId.fromString(rev2);
        return new Git(repository).log().addRange(fromObjectID, toObjectID).call();
    }

    private static ObjectId getActualRefObjectId(Repository repo, Ref ref) {
        final Ref repoPeeled = repo.peel(ref);
        if (repoPeeled.getPeeledObjectId() != null) {
            return repoPeeled.getPeeledObjectId();
        }
        return ref.getObjectId();
    }

    private static List<String> changedFiles(Repository repo, String rev1, String rev2) throws IOException, GitAPIException {
        Set<String> result = new TreeSet<>();
        LogCommand command = new Git(repo).log();
        if (StringUtils.isNotEmpty(rev1)) {
            Ref ref1 = repo.findRef(rev1);
            ObjectId objectId1 = ref1 != null ? getActualRefObjectId(repo, ref1) : ObjectId.fromString(rev1);
            command.not(objectId1);
        }
        if (StringUtils.isNotEmpty(rev2)) {
            Ref ref2 = repo.findRef(rev2);
            ObjectId objectId2 = ref2 != null ? getActualRefObjectId(repo, ref2) : ObjectId.fromString(rev2);
            command.add(objectId2);
        }
        Iterable<RevCommit> revCommits = command.call();
        for (RevCommit commit : revCommits) {
            CanonicalTreeParser cp = new CanonicalTreeParser();
            try (ObjectReader reader = repo.newObjectReader()) {
                cp.reset(reader, commit.getTree());
            }
            for (RevCommit parent : commit.getParents()) {
                CanonicalTreeParser pp = new CanonicalTreeParser();
                try (ObjectReader reader = repo.newObjectReader()) {
                    pp.reset(reader, parent.getTree());
                }
                List<DiffEntry> diffEntries = new Git(repo).diff().setOldTree(pp).setNewTree(cp).setShowNameAndStatusOnly(true).call();
                for (DiffEntry diffEntry : diffEntries) {
                    if (diffEntry.getChangeType() != DiffEntry.ChangeType.DELETE) {
                        String path = diffEntry.getPath(DiffEntry.Side.NEW);
                        result.add(path);
                    }
                }
            }
        }
        return new ArrayList<>(result);
    }

    public Date getLastModified(GitPath gfsPath) throws IOException {
        GitFileSystem gfs = getCurrentGfs();
        if (!Files.exists(gfsPath)) {
            return null;
        }
        RevCommit commit = getLastCommit(gfs, gfsPath);
//        PersonIdent authorIdent = commit.getAuthorIdent();
//        Date authorDate = authorIdent.getWhen();
        return commit == null ? null : new Date(commit.getCommitTime() * 1000L);
    }

    public RevCommit getLastCommit(GitFileSystem gfs, GitPath gfsPath) throws IOException {
        String relPath = gfs.getRootPath().relativize(gfsPath).toString();
        try (RevWalk revCommits = new RevWalk(gfs.getRepository())) {
            revCommits.setTreeFilter(PathFilter.create(relPath));
            ObjectId branchId = gfs.getRepository().resolve(gfs.getStatusProvider().branch());
            revCommits.markStart(revCommits.parseCommit(branchId));
            RevCommit last = revCommits.next();
            return last;
        }
    }

    public Map importEntityByName(Map entity) throws Exception {
        inGitTransaction("importTransformation", (Callable<Void>) () -> {
            Path modelPath = getCurrentGfs().getPath("/model");
            Files.walk(modelPath)
                    .filter(Files::isRegularFile).
                    filter(file -> file.getFileName().toString().equalsIgnoreCase(entity.get("name") + ".xmi"))
                    .forEach(path -> importXmiPath(path));
            Files.walk(modelPath)
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().equalsIgnoreCase(entity.get("name") + ".ref"))
                    .forEach(path -> importRefPath(path));
            return null;
        });
        Map result = new HashMap() {{
            put("result", true);
            put("problems", false);
        }};
        return result;
    }

    public void deleteEObject(EObject eObject) {
        EClass eClass = eObject.eClass();
        String dir = "/model/" + eClass.getEPackage().getNsPrefix() + "/" + eClass.getName();
        EStructuralFeature nameFeature = eObject.eClass().getEStructuralFeature("name");
        if (nameFeature == null && !(nameFeature instanceof EAttribute)) {
            logger.info("EObject has no name attribute: " + eObject);
            return;
        }
        String name = (String) eObject.eGet(nameFeature);
        if (name == null || name.length() == 0) {
            logger.info("EObject has empty name");
            return;
        }
        String sourcesDir = "/sources/" + eClass.getName() + "/" + name;

        try {
            inGitTransaction("deleteEObject " + dir + "/" + name, (Callable<Void>) () -> {
                Path modelPath = getCurrentGfs().getPath(dir);
                Path sourcesPath = getCurrentGfs().getPath(sourcesDir);
                if (!Files.isDirectory(sourcesPath)) {
                    return null;
                }
                Files.walk(sourcesPath)
                        .filter(Files::isRegularFile).forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                // pass
                            }
                        });
                if (!Files.isDirectory(modelPath)) {
                    return null;
                }

                Files.walk(modelPath)
                        .filter(Files::isRegularFile).
                        filter(file -> file.getFileName().toString().startsWith(name + "."))
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                // pass
                            }
                        });


                return null;
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
