package ru.neoflex.meta.emfgit;

import com.beijunyi.parallelgit.filesystem.GitFileSystem;
import com.beijunyi.parallelgit.filesystem.GitPath;
import com.beijunyi.parallelgit.utils.BranchUtils;
import com.beijunyi.parallelgit.utils.RepositoryUtils;
import com.beijunyi.parallelgit.utils.exceptions.RefUpdateLockFailureException;
import com.beijunyi.parallelgit.utils.exceptions.RefUpdateRejectedException;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMLParserPool;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.emf.ecore.xmi.impl.XMLParserPoolImpl;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.eclipse.emf.ecore.xmi.XMLResource.*;
import static org.eclipse.jgit.lib.Constants.DOT_GIT;

public class Database implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(Database.class);
    final public static String DB_PATH = "db";
    final public static String IDS_PATH = DB_PATH + "/ids";
    final public static String IDX_PATH = DB_PATH + "/idx";
    public static final String TYPE_NAME_IDX = "type_name";
    public static final String REF_IDX = "ref";
    public static final String QNAME = "name";
    public static final String GITDB = "gitdb";
    private final Repository repository;
    private List<EPackage> packages;
    private Map<String, Index> indexes = new HashMap<>();
    private Events events = new Events();
    private ReadWriteLock lock = new ReentrantReadWriteLock();
    private Function<EClass, EStructuralFeature> qualifiedNameDelegate;
    private Map<EClass, List<EClass>> descendants = new HashMap<>();
    private String repoName;
    private XMLParserPool xmlParserPool = new XMLParserPoolImpl();

    {
        try {
            GitURLStreamHandler.registerFactory();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Database(String repoPath, List<EPackage> packages) throws IOException, GitAPIException {
        this.repoName = new File(repoPath).getName();
        this.repository = openRepository(repoPath);
        this.packages = packages;
        preCalcDescendants();
        createTypeNameIndex();
        createRefIndex();
        registerEvents();
    }

    private void preCalcDescendants() {
        for (EPackage ePackage : this.packages) {
            for (EClassifier eClassifier : ePackage.getEClassifiers()) {
                if (eClassifier instanceof EClass) {
                    EClass eClass = (EClass) eClassifier;
                    if (!eClass.isAbstract()) {
                        for (EClass superType : eClass.getEAllSuperTypes()) {
                            getConcreteDescendants(superType).add(eClass);
                        }
                    }
                }
            }
        }
    }

    public List<EClass> getConcreteDescendants(EClass eClass) {
        return descendants.computeIfAbsent(eClass, (c) -> new ArrayList<EClass>() {
            {
                if (!eClass.isAbstract()) {
                    add(eClass);
                }
            }
        });
    }

    private void registerEvents() {
        events.registerBeforeSave(this::checkUniqueQName);
        events.registerAfterSave(this::updateResourceIndexes);
        events.registerBeforeDelete(this::checkDependencies);
        events.registerBeforeDelete(this::deleteResourceIndexes);
    }

    public EStructuralFeature getQNameFeature(EClass eClass) {
        if (qualifiedNameDelegate != null) {
            return qualifiedNameDelegate.apply(eClass);
        }
        return eClass.getEStructuralFeature(QNAME);
    }

    public EStructuralFeature checkAndGetQNameFeature(EClass eClass) {
        EStructuralFeature feature = getQNameFeature(eClass);
        if (feature == null) {
            throw new IllegalArgumentException(String.format(
                    "Qualified name not found for eClass: %s", EcoreUtil.getURI(eClass)));
        }
        return eClass.getEStructuralFeature("name");
    }


    private void createTypeNameIndex() {
        createIndex(new Index() {
            @Override
            public String getName() {
                return TYPE_NAME_IDX;
            }

            @Override
            public List<IndexEntry> getEntries(Resource resource, Transaction transaction) throws IOException {
                ArrayList<IndexEntry> result = new ArrayList<>();
                EObject eObject = resource.getContents().get(0);
                EClass eClass = eObject.eClass();
                EStructuralFeature nameSF = getQNameFeature(eClass);
                if (nameSF != null) {
                    String name = (String) eObject.eGet(nameSF);
                    if (name == null || name.length() == 0) {
                        throw new IOException("Empty feature name");
                    }
                    EPackage ePackage = eClass.getEPackage();
                    IndexEntry entry = new IndexEntry();
                    entry.setPath(new String[]{ePackage.getNsURI(), eClass.getName(), name});
                    entry.setContent(getResourceId(resource).getBytes(StandardCharsets.UTF_8));
                    result.add(entry);
                }
                return result;
            }
        });
    }

    private void createRefIndex() {
        createIndex(new Index() {
            @Override
            public String getName() {
                return REF_IDX;
            }

            @Override
            public List<IndexEntry> getEntries(Resource resource, Transaction transaction) throws IOException {
                ArrayList<IndexEntry> result = new ArrayList<>();
                Map<EObject, Collection<EStructuralFeature.Setting>> cr = EcoreUtil.ExternalCrossReferencer.find(resource);
                Set<String> rootIds = new HashSet<>();
                for (EObject eObject : cr.keySet()) {
                    EObject root = EcoreUtil.getRootContainer(eObject);
                    Resource rootResource = root.eResource();
                    if (rootResource != null) {
                        String id = checkAndGetResourceId(rootResource);
                        rootIds.add(id);
                    }
                }
                for (String id: rootIds) {
                    IndexEntry entry = new IndexEntry();
                    entry.setPath(new String[]{id.substring(0, 2), id.substring(2), getId(resource.getURI())});
                    entry.setContent(new byte[0]);
                    result.add(entry);
                }
                return result;
            }
        });
    }

    public Resource loadResource(byte[] content, Resource resource) throws IOException {
        ((XMIResourceImpl) resource).doLoad(new ByteArrayInputStream(content), null);
        return resource;
    }

    public Resource createResource(Transaction tx, String id, String rev) {
        ResourceSet resourceSet = createResourceSet(tx);
        return createResource(resourceSet, id, rev);
    }

    public Resource createResource(ResourceSet resourceSet, String id, String rev) {
        URI uri = createURI(id, rev);
        return resourceSet.createResource(uri);
    }

    public URI createURI(String id, String rev) {
        StringBuffer buffer = new StringBuffer();
        if (id != null) {
            buffer.append(id);
        }
        if (rev != null) {
            buffer.append("?rev=");
            buffer.append(rev);
        }
        return createURIByRef(buffer.toString());
    }

    public URI createURIByRef(String ref) {
        URI uri = URI.createURI(GITDB + "://" +repoName + "/" + (ref == null ? "" : ref));
        return uri;
    }

    public ResourceSet createResourceSet() {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getPackageRegistry()
                .put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);
        for (EPackage ePackage : packages) {
            resourceSet.getPackageRegistry()
                    .put(ePackage.getNsURI(), ePackage);
        }
        resourceSet.getResourceFactoryRegistry()
                .getExtensionToFactoryMap()
                .put("*", new XMIResourceFactoryImpl() {
                    @Override
                    public Resource createResource(URI uri) {
                        XMIResourceImpl resource = new XMIResourceImpl(uri);
                        resource.getDefaultSaveOptions().put(OPTION_ENCODING, "UTF-8");
                        resource.getDefaultSaveOptions().put(OPTION_CONFIGURATION_CACHE, true);
                        resource.getDefaultLoadOptions().put(OPTION_USE_PARSER_POOL, xmlParserPool);
                        resource.getDefaultLoadOptions().put(OPTION_USE_DEPRECATED_METHODS, false);
                        resource.getDefaultLoadOptions().put(OPTION_DEFER_ATTACHMENT, true);
                        resource.getDefaultLoadOptions().put(OPTION_DEFER_IDREF_RESOLUTION, true);
                        return resource;
                    }
                });
        return resourceSet;
    }

    public ResourceSet createResourceSet(Transaction tx) {
        ResourceSet resourceSet = createResourceSet();
        resourceSet.getURIConverter()
                .getURIHandlers()
                .add(0, new GitHandler(tx));
        return resourceSet;
    }

    public String getRev(URI uri) throws IOException {
        String query = uri.query();
        if (query == null || !query.contains("rev=")) {
            return null;
        }
        return query.split("rev=")[1];
    }

    public String checkAndGetRev(URI uri) throws IOException {
        String rev = getRev(uri);
        if (rev == null) {
            throw new IOException("Revision not found: " + uri.toString());
        }
        return rev;
    }

    public String getId(URI uri) {
        if (uri.segmentCount() == 0) {
            return null;
        }
        return uri.segment(0);
    }

    public String getResourceId(Resource resource) {
        return getId(resource.getURI());
    }

    public String checkAndGetId(URI uri) throws IOException {
        String id = getId(uri);
        if (id == null) {
            throw new IOException("Can't handle resource without id: " + uri.toString());
        }
        return id;
    }

    public String checkAndGetResourceId(Resource resource) throws IOException {
        return checkAndGetId(resource.getURI());
    }

    public byte[] getResourceContent(Resource resource) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        resource.save(os, null);
        return os.toByteArray();
    }

    public List<Resource> getDependentResources(Resource resource, Transaction tx) throws IOException {
        String id = checkAndGetResourceId(resource);
        List<Resource> resources = getDependentResources(id, tx);
        return resources;
    }

    public List<Resource> getDependentResources(List<Resource> resources, Transaction tx) throws IOException {
        List<Resource> result = new ArrayList<>();
        for (Resource resource: resources) {
            if (!result.stream().anyMatch(r->r.getURI().equals(resource.getURI()))) {
                result.add(resource);
            }
            for (Resource dep: getDependentResources(resource, tx)) {
                if (!result.stream().anyMatch(r->r.getURI().equals(dep.getURI()))) {
                    result.add(dep);
                }
            }
        }
        return result;
    }

    public List<Resource> getDependentResources(List<Resource> resources, Transaction tx, boolean recursive) throws IOException {
        if (recursive) {
            return getDependentResourcesRecursive(resources, tx);
        }
        else {
            return getDependentResources(resources, tx);
        }
    }

    public List<Resource> getDependentResourcesRecursive(List<Resource> resources, Transaction tx) throws IOException {
        List<Resource> result = new ArrayList<>();
        Queue<Resource> queue = new ArrayDeque<>(resources);
        while (!queue.isEmpty()) {
            Resource resource = queue.remove();
            if (!result.stream().anyMatch(r->r.getURI().equals(resource.getURI()))) {
                result.add(resource);
                queue.addAll(getDependentResources(resource, tx));
            }
        }
        return result;
    }

    public List<Resource> getDependentResources(String id, Transaction tx) throws IOException {
        ResourceSet resourceSet = createResourceSet(tx);
        List<Resource> resources = new ArrayList<>();
        List<IndexEntry> refList = findByIndex(tx, REF_IDX, id.substring(0, 2), id.substring(2));
        for (IndexEntry entry : refList) {
            String refId = entry.getPath()[entry.getPath().length - 1];
            resources.add(loadResource(resourceSet, refId));
        }
        return resources;
    }

    public ResourceSet findByEClass(EClass eClass, String name, Transaction tx) throws IOException {
        ResourceSet resourceSet = createResourceSet(tx);
        List<IndexEntry> ieList = findEClassIndexEntries(eClass, name, tx);
        for (IndexEntry entry : ieList) {
            String id = new String(entry.getContent());
            loadResource(resourceSet, id);
        }
        return resourceSet;
    }

    public List<IndexEntry> findEClassIndexEntries(EClass eClass, String name, Transaction tx) throws IOException {
        String nsURI = eClass.getEPackage().getNsURI();
        String className = eClass.getName();
        List<IndexEntry> ieList;
        if (name == null || name.length() == 0) {
            ieList = findByIndex(tx, TYPE_NAME_IDX, nsURI, className);
        } else {
            ieList = findByIndex(tx, TYPE_NAME_IDX, nsURI, className, name);
        }
        return ieList;
    }

    public Resource loadResource(ResourceSet resourceSet, String id) throws IOException {
        URI uri = createURI(id, null);
        Resource resource = resourceSet.createResource(uri);
        resource.load(null);
        return resource;
    }

    public Resource loadResource(String id, Transaction tx) throws IOException {
        ResourceSet resourceSet = createResourceSet(tx);
        return loadResource(resourceSet, id);
    }

    public List<EPackage> getPackages() {
        return packages;
    }

    private void checkDependencies(Resource old, Transaction tx) throws IOException {
        String id = getId(old.getURI());
        List<IndexEntry> refList = findByIndex(tx, "ref", id.substring(0, 2), id.substring(2));
        if (!refList.isEmpty()) {
            String[] path = refList.get(0).getPath();
            throw new IOException("Object " + id + " is referenced by " + path[path.length - 1]);
        }
    }

    private void deleteResourceIndexes(Resource old, Transaction tx) throws IOException {
        GitFileSystem gfs = tx.getFileSystem();
        for (String indexName : getIndexes().keySet()) {
            GitPath indexPath = gfs.getPath("/", IDX_PATH, indexName);
            for (IndexEntry entry : getIndexes().get(indexName).getEntries(old, tx)) {
                GitPath indexValuePath = indexPath.resolve(gfs.getPath(".", entry.getPath()).normalize());
                Files.delete(indexValuePath);
            }
        }
    }

    private void updateResourceIndexes(Resource oldResource, Resource newResource, Transaction tx) throws IOException {
        GitFileSystem gfs = tx.getFileSystem();
        Set<String> toDelete = new HashSet<>();
        if (oldResource != null) {
            for (String indexName : getIndexes().keySet()) {
                GitPath indexPath = gfs.getPath("/", IDX_PATH, indexName);
                for (IndexEntry entry : getIndexes().get(indexName).getEntries(oldResource, tx)) {
                    GitPath indexValuePath = indexPath.resolve(gfs.getPath(".", entry.getPath()).normalize());
                    toDelete.add(indexValuePath.toString());
                }
            }
        }
        for (String indexName : getIndexes().keySet()) {
            GitPath indexPath = gfs.getPath("/", IDX_PATH, indexName);
            for (IndexEntry entry : getIndexes().get(indexName).getEntries(newResource, tx)) {
                GitPath indexValuePath = indexPath.resolve(gfs.getPath(".", entry.getPath()).normalize());
                if (!toDelete.remove(indexValuePath.toString()) && Files.exists(indexValuePath)) {
                    throw new IOException("Index file " + indexValuePath.toString() + " already exists");
                }
                Files.createDirectories(indexValuePath.getParent());
                Files.write(indexValuePath, entry.getContent());
            }
        }
        for (String indexValuePathString : toDelete) {
            GitPath indexValuePath = gfs.getPath(indexValuePathString);
            Files.delete(indexValuePath);
        }
    }

    public void reindex(Transaction tx) throws IOException {
        GitFileSystem gfs = tx.getFileSystem();
        GitPath indexRootPath = gfs.getPath("/", IDX_PATH);
        deleteRecursive(indexRootPath);
        for (EntityId entityId : tx.all()) {
            Entity entity = tx.load(entityId);
            Resource resource = entityToResource(tx, entity);
            updateResourceIndexes(null, resource, tx);
        }
    }

    public Resource entityToResource(Transaction tx, Entity entity) throws IOException {
        Resource resource = createResource(tx, entity.getId(), entity.getRev());
        loadResource(entity.getContent(), resource);
        return resource;
    }

    public void deleteRecursive(Path indexRootPath) throws IOException {
        List<Path> pathsToDelete = Files.walk(indexRootPath).sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        for (Path path : pathsToDelete) {
            Files.deleteIfExists(path);
        }
    }

    private void checkUniqueQName(Resource resourceOld, Resource resource, Transaction tx) throws IOException {
        if (resource.getContents().isEmpty()) {
            return;
        }
        EObject eObject = resource.getContents().get(0);
        EClass eClass = eObject.eClass();
        EStructuralFeature sf = getQNameFeature(eClass);
        if (sf != null) {
            String name = (String) eObject.eGet(sf);
            if (resourceOld != null && resourceOld.getContents().size() > 0) {
                String oldName = (String) resource.getContents().get(0).eGet(sf);
                if (Objects.equals(oldName, name)) {
                    return;
                }
            }
            String id = resourceOld == null ? null : checkAndGetResourceId(resourceOld);
            for (EClass descendant : getConcreteDescendants(eClass)) {
                List<IndexEntry> ieList = findEClassIndexEntries(descendant, name, tx);
                for (IndexEntry ie : ieList) {
                    String oldId = new String(ie.getContent());
                    if (id == null || !id.equals(oldId)) {
                        throw new IllegalArgumentException(String.format(
                                "Duplicate qualified name eClass: %s, feature: %s, id: %s",
                                EcoreUtil.getURI(eClass), name, oldId));
                    }
                }
            }
        }
    }

    public List<IndexEntry> findByIndex(Transaction tx, String indexName, String... path) throws IOException {
        GitFileSystem gfs = tx.getFileSystem();
        GitPath indexPath = gfs.getPath("/", IDX_PATH, indexName);
        GitPath indexValuePath = indexPath.resolve(gfs.getPath(".", path).normalize());
        try {
            return Files.walk(indexValuePath).filter(Files::isRegularFile).map(file -> {
                IndexEntry entry = new IndexEntry();
                Path relPath = indexPath.relativize(file);
                entry.setPath(relPath.toString().split("/"));
                try {
                    byte[] content = Files.readAllBytes(file);
                    entry.setContent(content);
                    return entry;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toList());
        } catch (NoSuchFileException e) {
            return new ArrayList<>();
        }
    }

    public Set<String> getBranches() throws IOException {
        return BranchUtils.getBranches(repository).keySet();
    }

    public void createBranch(String branch, String from) throws IOException {
        BranchUtils.createBranch(branch, from, repository);
    }

    public Transaction createTransaction(String branch) throws IOException {
        return new Transaction(this, branch);
    }

    public Transaction createTransaction(String branch, Transaction.LockType lockType) throws IOException {
        return new Transaction(this, branch, lockType);
    }

    public <R> R inTransaction(String branch, Transaction.LockType lockType, Transactional<R> f) throws Exception {
        return inTransaction(() -> createTransaction(branch, lockType), f);
    }

    public interface TxSupplier<T> {
        T get() throws Exception;
    }

    public <R> R inTransaction(TxSupplier<Transaction> transactionSupplier, Transactional<R> f) throws Exception {
        int delay = 1;
        int maxDelay = 1000;
        int maxAttempts = 100;
        int attempt = 1;
        while (true) {
            try (Transaction tx = transactionSupplier.get()) {
                return tx.withCurrent(() -> f.call(tx));
            }
            catch (RefUpdateLockFailureException|RefUpdateRejectedException e) {
                String message = e.getClass().getSimpleName() + ": " + e.getMessage() + " attempt no " + attempt;
                logger.debug(message);
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

    @Override
    public void close() throws IOException {
        repository.close();
    }

    public Repository getRepository() {
        return repository;
    }

    public Repository openRepository(String repoPath) throws IOException, GitAPIException {
        File repoFile = new File(repoPath);
        Repository repository = new File(repoFile, DOT_GIT).exists() ?
                RepositoryUtils.openRepository(repoFile, false) :
                RepositoryUtils.createRepository(repoFile, false);
        if (BranchUtils.getBranches(repository).size() == 0) {
            try (Git git = new Git(repository)) {
                git.commit().setMessage("Initial commit").setAllowEmpty(true).call();
            }
        }
        return repository;
    }

    public Map<String, Index> getIndexes() {
        return indexes;
    }

    public void createIndex(Index index) {
        getIndexes().put(index.getName(), index);
    }

    public ReadWriteLock getLock() {
        return lock;
    }

    public Events getEvents() {
        return events;
    }

    public Function<EClass, EStructuralFeature> getQualifiedNameDelegate() {
        return qualifiedNameDelegate;
    }

    public void setQualifiedNameDelegate(Function<EClass, EStructuralFeature> qualifiedNameDelegate) {
        this.qualifiedNameDelegate = qualifiedNameDelegate;
    }

    public interface Transactional<R> {
        R call(Transaction tx) throws Exception;
    }
}
