package ru.neoflex.meta.emfgit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;

import java.io.*;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Exporter {
    public static final String EXTERNAL_REFERENCES = "externalReferences";
    public static final String REF_OBJECT = "refObject";
    public static final String FEATURES = "features";
    public static final String FRAGMENT = "fragment";
    public static final String NAME = "name";
    public static final String XMI = ".xmi";
    public static final String REFS = ".refs";

    Database database;

    public Exporter(Database database) {
        this.database = database;
    }

    public ObjectNode objectToTree(EObject eObject) {
        ObjectNode objectNode = new ObjectMapper().createObjectNode();
        EObject rootContainer = EcoreUtil.getRootContainer(eObject);
        String fragment = EcoreUtil.getRelativeURIFragmentPath(rootContainer, eObject);
        objectNode.put("eClass", eClass2String(rootContainer.eClass()));
        EStructuralFeature nameAttribute = database.checkAndGetQNameFeature(rootContainer.eClass());
        objectNode.put(NAME, (String) rootContainer.eGet(nameAttribute));
        objectNode.put(FRAGMENT, fragment);
        return objectNode;
    }

    public byte[] exportExternalRefs(EObject eObject) throws JsonProcessingException {
        ObjectNode objectNode = objectToTree(eObject);
        ArrayNode externalReferences = objectNode.withArray(EXTERNAL_REFERENCES);
        Map<EObject, Collection<EStructuralFeature.Setting>> crs = EcoreUtil.ExternalCrossReferencer.find(Collections.singleton(eObject));
        if (crs.size() == 0) {
            return null;
        }
        for (EObject refObject: crs.keySet()) {
            ObjectNode externalReference = externalReferences.addObject();
            externalReference.set(REF_OBJECT, objectToTree(refObject));
            ArrayNode features = externalReference.putArray(FEATURES);
            for (EStructuralFeature.Setting setting: crs.get(refObject)) {
                ObjectNode feature = features.addObject();
                feature.put(NAME, setting.getEStructuralFeature().getName());
                EObject child = setting.getEObject();
                String fragment = EcoreUtil.getRelativeURIFragmentPath(eObject, child);
                feature.put(FRAGMENT, fragment);
            }
        }
        return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(objectNode);
    }

    public byte[] exportEObjectWithoutExternalRefs(EObject eObject) throws IOException {
        ResourceSet resourceSet = database.createResourceSet();
        Resource resource = resourceSet.createResource(eObject.eResource().getURI());
        EObject copyObject = EcoreUtil.copy(eObject);
        resource.getContents().add(copyObject);
        unsetExternalReferences(copyObject);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        resource.save(os, null);
        return os.toByteArray();
    }

    public void unsetExternalReferences(EObject eObject) {
        Map<EObject, Collection<EStructuralFeature.Setting>> crs = EcoreUtil.ExternalCrossReferencer.find(Collections.singleton(eObject));
        for (EObject refObject: crs.keySet()) {
            for (EStructuralFeature.Setting setting: crs.get(refObject)) {
                setting.unset();
            }
        }
    }


    public EObject treeToObject(JsonNode objectNode, Transaction tx) throws IOException {
        String uri = objectNode.get("eClass").textValue();
        String name = objectNode.get(NAME).textValue();
        if (name == null || name.length() == 0) {
            throw new IOException("Object " + uri + " with empty name not found");
        }
        EClass eClass = string2EClass(uri);
        ResourceSet resourceSet = database.findByEClass(eClass, name, tx);
        if (resourceSet.getResources().size() != 1) {
            throw new IOException("Object " + uri + "[" + name + "] not found or not unique");
        }
        EObject eObject = resourceSet.getResources().get(0).getContents().get(0);
        String fragment = objectNode.get(FRAGMENT).textValue();
        return fragment == null || fragment.length() == 0 ? eObject : EcoreUtil.getEObject(eObject, fragment);
    }


    public void exportEObject(EObject eObject, Path path) throws IOException {
        EClass eClass = eObject.eClass();
        EPackage ePackage = eClass.getEPackage();
        EStructuralFeature nameAttribute = database.getQNameFeature(eClass);
        if (nameAttribute != null) {
            String name = (String) eObject.eGet(nameAttribute);
            if (name != null && name.length() > 0) {
                byte[] bytes = exportEObjectWithoutExternalRefs(eObject);
                String fileName = ePackage.getName() + "_" + eClass.getName() + "_" + name;
                Path filePath = path.resolve(fileName + XMI);
                Files.createDirectories(filePath.getParent());
                Files.write(filePath, bytes);
                byte[] refsBytes = exportExternalRefs(eObject);
                if (refsBytes != null) {
                    Path refsPath = path.resolve(fileName + REFS);
                    Files.createDirectories(refsPath.getParent());
                    Files.write(refsPath, refsBytes);
                }
            }
        }
    }

    public void exportResourceSet(ResourceSet resourceSet, Path path) throws IOException {
        for (Resource resource: resourceSet.getResources()) {
            for (EObject eObject: resource.getContents()) {
                exportEObject(eObject, path);
            }
        }
    }

    public void zip(ResourceSet resourceSet, OutputStream outputStream) throws IOException {
        List<Resource> resources = resourceSet.getResources();
        zip(resources, outputStream);
    }

    public void zip(List<Resource> resources, OutputStream outputStream) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (Resource resource: resources) {
                zipResource(zipOutputStream, resource);
            }
            for (Resource resource: resources) {
                zipResourceReferences(zipOutputStream, resource);
            }
        }
    }

    public void zipAll(String branch, OutputStream outputStream) throws Exception {
        database.inTransaction(branch, Transaction.LockType.READ, tx -> {
            List<EntityId> all = tx.all();
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
                for (EntityId entityId: all) {
                    Resource resource = database.loadResource(entityId.getId(), tx);
                    zipResource(zipOutputStream, resource);
                }
                for (EntityId entityId: all) {
                    Resource resource = database.loadResource(entityId.getId(), tx);
                    zipResourceReferences(zipOutputStream, resource);
                }
            }
            return null;
        });
    }

    public void zipResourceReferences(ZipOutputStream zipOutputStream, Resource resource) throws IOException {
        for (EObject eObject: resource.getContents()) {
            EClass eClass = eObject.eClass();
            EPackage ePackage = eClass.getEPackage();
            EStructuralFeature nameAttribute = database.getQNameFeature(eClass);
            if (nameAttribute != null) {
                String name = (String) eObject.eGet(nameAttribute);
                if (name != null && name.length() > 0) {
                    String fileName = ePackage.getName() + "_" + eClass.getName() + "_" + name;
                    byte[] refsBytes = exportExternalRefs(eObject);
                    if (refsBytes != null) {
                        ZipEntry refsEntry = new ZipEntry(fileName + REFS);
                        zipOutputStream.putNextEntry(refsEntry);
                        zipOutputStream.write(refsBytes);
                        zipOutputStream.closeEntry();
                    }
                }
            }
        }
    }

    public void zipResource(ZipOutputStream zipOutputStream, Resource resource) throws IOException {
        for (EObject eObject: resource.getContents()) {
            EClass eClass = eObject.eClass();
            EPackage ePackage = eClass.getEPackage();
            EStructuralFeature nameAttribute = database.getQNameFeature(eClass);
            if (nameAttribute != null) {
                String name = (String) eObject.eGet(nameAttribute);
                if (name != null && name.length() > 0) {
                    String fileName = ePackage.getName() + "_" + eClass.getName() + "_" + name;
                    byte[] bytes = exportEObjectWithoutExternalRefs(eObject);
                    ZipEntry zipEntry = new ZipEntry(fileName + XMI);
                    zipOutputStream.putNextEntry(zipEntry);
                    zipOutputStream.write(bytes);
                    zipOutputStream.closeEntry();
                }
            }
        }
    }

    public int unzip(InputStream inputStream, Transaction tx) throws IOException {
        int entityCount = 0;
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            while (zipEntry != null) {
                if (!zipEntry.isDirectory()) {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int length;
                    while ((length = zipInputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, length);
                    }
                    if (zipEntry.getName().endsWith(XMI)) {
                        importEObject(outputStream.toByteArray(), tx);
                        ++entityCount;
                    }
                    else if (zipEntry.getName().endsWith(REFS)) {
                        importExternalRefs(outputStream.toByteArray(), tx);
                    }
                }
                zipEntry = zipInputStream.getNextEntry();
            }
        }
        return entityCount;
    }

    public void unzip(Path zipFile, Transaction tx) throws IOException {
        Map<String, Object> env = new HashMap<>();
        //env.put("create", "true");
        env.put("useTempFile", Boolean.TRUE);
        java.net.URI uri = java.net.URI.create("jar:" + zipFile.toUri());
        FileSystem fileSystem = FileSystems.newFileSystem(uri, env);
        Iterable<Path> roots = fileSystem.getRootDirectories();
        Path root = roots.iterator().next();
        importPath(root, tx);
    }

    public void exportAll(String branch, Path path) throws Exception {
        List<EntityId> all = database.inTransaction(branch, Transaction.LockType.READ, Transaction::all);
        for (EntityId entityId: all) {
            database.inTransaction(branch, Transaction.LockType.READ, tx -> {
                Resource resource = database.loadResource(entityId.getId(), tx);
                for (EObject eObject: resource.getContents()) {
                    exportEObject(eObject, path);
                }
                return resource;
            });
        }
    }

    public void importPath(Path path, Transaction tx) throws IOException {
        List<Path> jsonPaths = Files.walk(path).filter(Files::isRegularFile).filter(file -> file.getFileName().toString().endsWith(XMI)).collect(Collectors.toList());
        for (Path jsonPath : jsonPaths) {
            byte[] content = Files.readAllBytes(jsonPath);
            importEObject(content, tx);
        }
        List<Path> refsPaths = Files.walk(path).filter(Files::isRegularFile).filter(file -> file.getFileName().toString().endsWith(REFS)).collect(Collectors.toList());
        for (Path refsPath : refsPaths) {
            byte[] content = Files.readAllBytes(refsPath);
            importExternalRefs(content, tx);
        }
    }

    public EObject importEObject(byte[] image, Transaction tx) throws IOException {
        Resource resource = database.createResource(tx, null, null);
        resource.load(new ByteArrayInputStream(image), null);
        EObject eObject = resource.getContents().get(0);
        EClass eClass = eObject.eClass();
        EStructuralFeature nameFeature = database.getQNameFeature(eClass);
        if (nameFeature == null) {
            throw new IOException("Qualified name not found in " + eClass2String(eClass));
        }
        String name = (String) eObject.eGet(nameFeature);
        ResourceSet existentRS = database.findByEClass(eClass, name, tx);
        if (existentRS.getResources().size() > 0) {
            Resource r = existentRS.getResources().get(0);
            resource.setURI(r.getURI());
        }
        resource.save(null);
        return eObject;
    }

    public EObject importExternalRefs(byte[] refs, Transaction tx) throws IOException {
        ObjectNode objectNode = (ObjectNode) new ObjectMapper().readTree(refs);
        EObject eObject = treeToObject(objectNode, tx);
        unsetExternalReferences(eObject);
        ArrayNode externalReferences = objectNode.withArray(EXTERNAL_REFERENCES);
        for (JsonNode externalReference: externalReferences) {
            EObject refObject = treeToObject(externalReference.get(REF_OBJECT), tx);
            for (JsonNode feature: externalReference.withArray(FEATURES)) {
                String fragment = feature.get(FRAGMENT).textValue();
                EObject referenceeObject = fragment == null || fragment.length() == 0 ? eObject : EcoreUtil.getEObject(eObject, fragment);
                String name = feature.get(NAME).textValue();
                EReference eReference = (EReference) referenceeObject.eClass().getEStructuralFeature(name);
                if (eReference == null || eReference.isContainment()) {
                    throw new RuntimeException("Non-contained EReference " + feature + " not found in object " + referenceeObject);
                }
                if (eReference.isMany()) {
                    EList eList = (EList) referenceeObject.eGet(eReference);
                    eList.add(refObject);
                }
                else {
                    referenceeObject.eSet(eReference, refObject);
                }
            }
        }
        eObject.eResource().save(null);
        return eObject;
    }

    public String eClass2String(EClass eClass) {
        return EcoreUtil.getURI(eClass).toString();
    }

    public EClass string2EClass(String uri) {
        return (EClass) database.createResourceSet().getEObject(URI.createURI(uri), false);
    }
}
