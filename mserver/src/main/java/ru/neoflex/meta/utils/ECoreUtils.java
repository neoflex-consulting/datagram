package ru.neoflex.meta.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang.StringUtils;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.EMap;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.impl.EEnumImpl;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.hibernate.Query;
import org.hibernate.internal.SessionImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.neoflex.meta.model.Database;
import ru.neoflex.meta.utils.teneo.MSerializableDynamicEObjectImpl;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;

/**
 * Created by orlov on 28.07.2017.
 */
public class ECoreUtils {
    public final static SimpleDateFormat jsonDateParser = new SimpleDateFormat("yyyy-MM-dd");
    public final static SimpleDateFormat jsonTimestampParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private static final Logger logger = LoggerFactory.getLogger(ECoreUtils.class);

    public static String getTypeName(EClass eClass) {
        return eClass.getEPackage().getNsPrefix() + "." + eClass.getName();
    }

    public static EClass findEClass(String typeName) {
        String[] parts = typeName.split("[.]", 2);
        EPackage ePackage = findEPackage(parts[0]);
        return findEClass(ePackage, parts[1]);
    }

    public static EClass findEClass(EPackage ePackage, String name) {
        EClassifier eClassifier = ePackage.getEClassifier(name);
        if (eClassifier == null || !(eClassifier instanceof EClass)) {
            throw new RuntimeException("EClass " + name + " not found");
        }
        return (EClass) eClassifier;
    }

    public static List<EPackage> allPackages() {
        return Arrays.asList(Context.getCurrent().getContextSvc().getTeneoSvc().getHbds().getEPackages());
    }

    public static List<EClass> allClasses() {
        List<EClass> result = new ArrayList<>();
        for (EPackage ePackage : allPackages()) {
            for (EClassifier eClassifier : ePackage.getEClassifiers()) {
                if (eClassifier instanceof EClass) {
                    result.add((EClass) eClassifier);
                }
            }
        }
        return result;
    }

    public static List<EClass> topLevelClasses() {
        List<EClass> result = new ArrayList<>();
        for (EClass eClass : allClasses()) {
            if (isTopLevel(eClass)) {
                result.add(eClass);
            }
        }
        return result;
    }

    public static List<EObject> allEObjects(EClass eClass) {
        String query = "from " + getTypeName(eClass);
        return Context.getCurrent().getSession().createQuery(query).list();
    }

    public static Set<EObject> graphReferencingObjects(EObject eObject) {
        Set<EObject> eObjectSet = referencingObjects(eObject);
        eObjectSet.addAll(referencingObjects(eObject.eContents()));
        return eObjectSet;
    }

    public static Set<EObject> referencingObjects(List<EObject> eObjects) {
        Set<EObject> eObjectSet = new HashSet<>();
        for (EObject eObject: eObjects) {
            eObjectSet.addAll(referencingObjects(eObject));
        }
        return eObjectSet;
    }

    public static Set<EObject> referencingObjects(EObject eObject) {
        Set<EObject> eObjectSet = new HashSet<>();
        if (eObject instanceof MSerializableDynamicEObjectImpl) {
            MSerializableDynamicEObjectImpl map = (MSerializableDynamicEObjectImpl) eObject;
            for (EClass referencingClass: allClasses()) {
                for (EReference eReference: referencingClass.getEReferences()) {
                    if (!eReference.isContainment() && !eReference.isContainer() && eReference.getEReferenceType().isSuperTypeOf(eObject.eClass())) {
                        String typeName = getTypeName(referencingClass);
                        String query = "select e from " + typeName + " e join e." + eReference.getName() +
                                " с where c.e_id=" + map.getId();
                        for (Object object: Context.getCurrent().getSession().createQuery(query).list()) {
                            if (object instanceof EObject) {
                                EObject ref = (EObject) object;
                                EObject root = getTopContainer(ref);
                                eObjectSet.add(root);
                            }
                        }
                    }
                }
            }
        }
        return eObjectSet;
    }

    public static EObject getTopContainer(EObject eObject) {
        EObject root = EcoreUtil.getRootContainer(eObject);
        while (root instanceof MSerializableDynamicEObjectImpl) {
            MSerializableDynamicEObjectImpl map = (MSerializableDynamicEObjectImpl) root;
            EObject currentRoot = null;
            for (EClass referencingClass: allClasses()) {
                for (EReference eReference: referencingClass.getEReferences()) {
                    if (eReference.isContainment() && eReference.getEReferenceType().isSuperTypeOf(root.eClass())) {
                        String typeName = getTypeName(referencingClass);
                        String query = "select e from " + typeName + " e join e." + eReference.getName() +
                                " с where c.e_id=" + map.getId();
                        List<Object> objects = Context.getCurrent().getSession().createQuery(query).list();
                        if (objects.size() > 0) {
                            currentRoot = EcoreUtil.getRootContainer((EObject) objects.get(0));
                            break;
                        }
                    }
                }
                if (currentRoot != null) {
                    break;
                }
            }
            if (currentRoot == null) {
                break;
            }
            root = currentRoot;
        }
        return root;
    }


    public static ObjectNode objectToTree(ObjectMapper mapper, EObject eObject) {
        ObjectNode objectNode = mapper.createObjectNode();
        EObject rootContainer = EcoreUtil.getRootContainer(eObject);
        String fragment = EcoreUtil.getRelativeURIFragmentPath(rootContainer, eObject);
        objectNode.put("_type_", getTypeName(rootContainer.eClass()));
        objectNode.put("name", (String) rootContainer.eGet(rootContainer.eClass().getEStructuralFeature("name")));
        objectNode.put("fragment", fragment);
        return objectNode;
    }

    public static ObjectNode objectWithRefsToTree(ObjectMapper mapper, EObject eObject) {
        ObjectNode objectNode = objectToTree(mapper, eObject);
        ArrayNode crossReferences = objectNode.withArray("crossReferences");
        new EcoreUtil.ExternalCrossReferencer(Collections.singleton(eObject)) {
            protected void add(InternalEObject child, EReference eReference, EObject refObject) {
                ObjectNode refNode = crossReferences.addObject();
                refNode.set("refObject", objectToTree(mapper, refObject));
                String fragment = EcoreUtil.getRelativeURIFragmentPath(eObject, child);
                refNode.put("fragment", fragment);
                refNode.put("feature", eReference.getName());
            }
            {
                crossReference();
            }
        };
        return objectNode;
    }

    public static EObject treeToObjectWithRefs(JsonNode objectNode) {
        Map<String, EObject> loaded = new HashMap<>();
        EObject eObject = treeToObject(objectNode, loaded);
        // clear external references
        Map<EObject, Collection<EStructuralFeature.Setting>> crs = EcoreUtil.ExternalCrossReferencer.find(Collections.singleton(eObject));
        for (EObject eObject1: crs.keySet()) {
            for (EStructuralFeature.Setting setting: crs.get(eObject1)) {
                setting.getEObject().eUnset(setting.getEStructuralFeature());
            }
        }
        // restore external references from json object
        for (JsonNode crossReference: objectNode.withArray("crossReferences")) {
            EObject refObject = treeToObject(crossReference.get("refObject"), loaded);
            String fragment = crossReference.get("fragment").textValue();
            EObject referenceeObject = StringUtils.isEmpty(fragment) ? eObject : EcoreUtil.getEObject(eObject, fragment);
            String feature = crossReference.get("feature").textValue();
            EReference eReference = (EReference) referenceeObject.eClass().getEStructuralFeature(feature);
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
        return eObject;
    }

    public static EObject treeToObject(JsonNode objectNode, Map<String, EObject> loaded) {
        String _type_ = objectNode.get("_type_").textValue();
        String name = objectNode.get("name").textValue();
        String loadedKey = _type_ + "." + name;
        EObject eObject = loaded.computeIfAbsent(loadedKey, new Function<String, EObject>() {
            @Override
            public EObject apply(String s) {
                return queryEObjectByName(_type_, name);
            }
        });
        if (eObject == null) {
            throw new RuntimeException("Object " + _type_ + "[" + name + "] not found");
        }
        String fragment = objectNode.get("fragment").textValue();
        return StringUtils.isEmpty(fragment) ? eObject : EcoreUtil.getEObject(eObject, fragment);
    }

    private static EObject queryEObjectByName(String _type_, String name) {
        List eObjects = Context.getCurrent().getSession().createQuery("from " + _type_ + " where name=:name").setParameter("name", name).list();
        if (eObjects.size() == 0) {
            return null;
        }
        if (eObjects.size() > 1) {
            throw new RuntimeException("Object " + _type_ + "[" + name + "] found " + eObjects.size() + " times");
        }
        return (EObject) eObjects.get(0);
    }

    public static List<Path> exportEObject(Path exportPath, EObject eObject, ResourceSet resourceSet) throws IOException {
        List<Path> result = new ArrayList<>();
        EStructuralFeature nameFeature = eObject.eClass().getEStructuralFeature("name");
        if (nameFeature == null && !(nameFeature instanceof EAttribute)) {
            logger.info("EObject has no name attribute: " + eObject);
            return result;
        }
        String name = (String) eObject.eGet(nameFeature);
        if (name == null || name.length() == 0) {
            logger.info("EObject has empty name");
            return result;
        }
        Files.createDirectories(exportPath);
        Path xmiPath = exportPath.resolve(name + ".xmi");
        URI uri = URI.createURI(xmiPath.toUri().toString());
        Resource resTo = resourceSet.createResource(uri);
        try {
            resTo.getContents().add(EcoreUtil.copy(eObject));
            EcoreUtil.resolveAll(resTo);
            logger.info("File " + xmiPath.toString() + " is being saved");
            Map options = new HashMap() {{
                put("PROCESS_DANGLING_HREF", "DISCARD");
            }};
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            resTo.save(os, options);
            Files.write(xmiPath, os.toByteArray());
            result.add(xmiPath);
        } finally {
            resTo.unload();
        }
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode objectNode = objectWithRefsToTree(mapper, eObject);
        if (objectNode.withArray("crossReferences").size() > 0) {
            Path refPath = exportPath.resolve(name + ".ref");
            Files.write(refPath, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectNode).getBytes(StandardCharsets.UTF_8));
            result.add(refPath);
        }
        return result;
    }

    public static EObject importEObject(Path xmiFile) throws IOException {
        byte[] content = Files.readAllBytes(xmiFile);
        Resource resFrom = EMFResource.createResourceSet().createResource(URI.createURI(xmiFile.toUri().toString()));
        resFrom.load(new ByteArrayInputStream(content), null);
        try {
            for (EObject root: resFrom.getContents()) {
                String type = getTypeName(root.eClass());
                String name = (String) root.eGet(root.eClass().getEStructuralFeature("name"));
                EObject eObject = queryEObjectByName(type, name);
                if (eObject != null) {
                    logger.info("Merge existing object: " + xmiFile.toString());
                    eObject = (EObject) prefetchEntity(EObjectMap.wrap(eObject));
                }
                eObject = (EObject) merge(EObjectMap.wrap(eObject), EObjectMap.wrap(root), true);
                return eObject;
            }
        }
        catch (Throwable all) {
            logger.error("Invalid data file: " + xmiFile.toString());
            Context.getCurrent().rollbackResources();
        }
        finally {
            resFrom.unload();
        }
        return null;
    }

    public static boolean isTopLevel(EClass eClass) {
        if (getAnnotation(eClass, "mspace.ui", "toplevel", "false").equals("true")) {
            return true;
        }
        if (eClass.isAbstract() || eClass.getEStructuralFeature("name") == null) {
            return false;
        }
        for (EReference eReference : eClass.getEAllReferences()) {
            if (eReference.isContainer()) {
                return false;
            }
        }
        for (EClass parent : allClasses()) {
            for (EReference eReference : parent.getEReferences()) {
                if (eReference.isContainment() && eReference.getEReferenceType().isSuperTypeOf(eClass)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static EPackage findEPackage(String nsPrefix) {
        for (EPackage result : allPackages()) {
            if (result.getNsPrefix().equals(nsPrefix)) {
                return result;
            }
        }
        throw new RuntimeException("package " + nsPrefix + " not found");
    }

    public static Map copyEntity(Map entity, String name) {
        if (entity == null) {
            return null;
        }
        Map created = new HashMap();
        Map result = copyContained(entity, created, true, name, null);
        copyReferences(entity, result, created);
        Context.getCurrent().getTxSession().update(result);
        return result;
    }
    
    public static Map copyEntityToDerivedClass(Map entity, String name, String targetClassName) {
        if (entity == null) {
            return null;
        }
        Map created = new HashMap();
        Map result = copyContained(entity, created, true, name, targetClassName);
        copyReferences(entity, result, created);
        Context.getCurrent().getTxSession().update(result);
        return result;
    }
    

    public static Map instantiate(String typeName) {
        return (Map) ((SessionImpl) Context.getCurrent().getSession()).instantiate(typeName, null);
    }

    private static Map createdOrInstantiateRef(Map entity, Map created) {
        if (entity == null) {
            return null;
        }
        String hash = getHash(entity);
        if (created.containsKey(hash)) {
            return (Map) created.get(hash);
        }
        return instantiateRef(entity);
    }

    private static Map instantiateRef(Map entity) {
        return (Map) Context.getCurrent().getSession().get((String) entity.get("_type_"), (Serializable) entity.get("e_id"));
    }

    private static String getHash(Map entity) {
        return entity.get("_type_") + "#" + entity.get("e_id");
    }

    private static void copyReferences(Map entity, Map result, Map created) {
        String typeName = (String) entity.get("_type_");
        EClass eClass = findEClass(typeName);
        for (EStructuralFeature eStructuralFeature : eClass.getEAllStructuralFeatures()) {
            if (eStructuralFeature instanceof EReference) {
                EReference eReference = (EReference) eStructuralFeature;
                if (!eReference.isContainment()) {
                    if (eReference.getUpperBound() == 1) {
                        Map refEntity = (Map) entity.get(eStructuralFeature.getName());
                        result.put(eStructuralFeature.getName(), createdOrInstantiateRef(refEntity, created));
                    } else {
                        List<Map> listOld = (List<Map>) entity.get(eStructuralFeature.getName());
                        List<Map> listNew = new ArrayList<>();
                        for (Map e : listOld) {
                            listNew.add(createdOrInstantiateRef(e, created));
                        }
                        result.put(eStructuralFeature.getName(), listNew);
                    }
                } else {
                    if (eReference.getUpperBound() == 1) {
                        Map valEntity = (Map) entity.get(eStructuralFeature.getName());
                        Map valResult = (Map) result.get(eStructuralFeature.getName());
                        if (valEntity != null && valResult != null) {
                            copyReferences(valEntity, valResult, created);
                        }
                    } else {
                        List<Map> listEntity = (List<Map>) entity.get(eStructuralFeature.getName());
                        List<Map> listResult = (List<Map>) result.get(eStructuralFeature.getName());
                        for (int i = 0; i < listEntity.size(); ++i) {
                            Map valEntity = listEntity.get(i);
                            Map valResult = listResult.get(i);
                            copyReferences(valEntity, valResult, created);
                        }
                    }
                }
            }
        }
    }

    private static Map copyContained(Map entity, Map created, boolean rename, String name, String targetTypeName) {
        if (entity == null) {
            return null;
        }
        String typeName = targetTypeName == null ? (String) entity.get("_type_") : targetTypeName;
        Map result = instantiate(typeName);
        EClass eClass = findEClass(typeName);
        for (EStructuralFeature eStructuralFeature : eClass.getEAllStructuralFeatures()) {
            if (eStructuralFeature instanceof EAttribute) {
                EAttribute eAttribute = (EAttribute) eStructuralFeature;
                if (eAttribute.getUpperBound() == 1) {
                    Object value = entity.get(eAttribute.getName());
                    result.put(eStructuralFeature.getName(), value);
                } else {
                    List<Object> listNew = (List) entity.get(eStructuralFeature.getName());
                    for (Object value : (List) entity.get(eAttribute.getName())) {
                        listNew.add(value);
                    }
                    result.put(eStructuralFeature.getName(), listNew);
                }
            } else if (eStructuralFeature instanceof EReference) {
                EReference eReference = (EReference) eStructuralFeature;
                if (!eReference.isContainment()) {
                    continue;
                }
                if (eReference.getUpperBound() == 1) {
                    result.put(eStructuralFeature.getName(), copyContained((Map) entity.get(eStructuralFeature.getName()), created, false, null, null));
                } else {
                    List<Map> listOld = (List) entity.get(eStructuralFeature.getName());
                    List<Map> listNew = (List) result.get(eStructuralFeature.getName());
                    for (Map e : listOld) {
                        listNew.add(copyContained(e, created, false, null, null));
                    }
                }
            }
        }
        if (rename && result.containsKey("name") && name != null) {
            result.put("name", name);
        }
        Context.getCurrent().getTxSession().save(result);
        created.put(getHash(entity), result);
        return result;
    }

    public static String getAnnotation(EModelElement element, String source, String detail, String defValue) {
        EAnnotation eAnnotation = element.getEAnnotation(source);
        if (eAnnotation != null) {
            EMap<String, String> details = eAnnotation.getDetails();
            if (details.containsKey(detail)) {
                return details.get(detail);
            }
        }
        return defValue;
    }

    public static List<EClass> getDescendants(EClass eClass) {
        List<EClass> descendants = new ArrayList<>();
        for (EPackage ePackage : allPackages()) {
            for (EClassifier eClassifier : ePackage.getEClassifiers()) {
                if (eClassifier instanceof EClass) {
                    EClass c = (EClass) eClassifier;
                    if (!c.isAbstract() && eClass.isSuperTypeOf(c) && !c.equals(eClass)) {
                        descendants.add(c);
                    }
                }
            }
        }
        return descendants;
    }

    public static List<EReference> getSuperAllReferences(EClass eClass) {
        List<EReference> eReferences = new ArrayList<>(eClass.getEAllReferences());
        for (EClass desc : getDescendants(eClass)) {
            eReferences.addAll(desc.getEReferences());
        }
        return eReferences;
    }

    public static List<EStructuralFeature> getSuperAllStructuredFeatures(EClass eClass) {
        List<EStructuralFeature> eEStructuralFeature = new ArrayList<>(eClass.getEAllStructuralFeatures());
        for (EClass desc : getDescendants(eClass)) {
            eEStructuralFeature.addAll(desc.getEStructuralFeatures());
        }
        return eEStructuralFeature;
    }

    public static List<EReference> getSplitPoints(EClass eClass) {
        List<EReference> splitPoints = new ArrayList<>();
        for (EReference sf : getSuperAllReferences(eClass)) {
            if (sf.isMany() && sf.isContainment()) {
                splitPoints.add(sf);
            }
        }
        if (splitPoints.size() <= 1) {
            splitPoints.clear();
        }
        return splitPoints;
    }

    public static List<String> generatePrefetches(EClass eClass, String alias, List<EReference> path) {
        List<String> result = new ArrayList<>();
        List<EReference> splitPoints = getSplitPoints(eClass);
        StringBuffer nonContained = new StringBuffer();
        for (EStructuralFeature sf : getSuperAllStructuredFeatures(eClass)) {
            String joinedAlias = alias + "_" + sf.getName();
            if (sf instanceof EAttribute && sf.isMany()) {
                nonContained.append(" left outer join fetch ").append(alias).append(".").append(sf.getName()).append(" ").append(joinedAlias);
            } else if (sf instanceof EReference) {
                EReference eReference = (EReference) sf;
                if (eReference.isContainer() || path.contains(eReference) || eReference.getEReferenceType().getName().equals("EObject")) {
                    continue;
                }
                if (!eReference.isContainment()) {
                    nonContained.append(" left outer join fetch ").append(alias).append(".").append(sf.getName()).append(" ").append(joinedAlias);
                } else {
                    List<EReference> newPath = new ArrayList<>(path);
                    newPath.add(eReference);
                    List<String> children = generatePrefetches(eReference.getEReferenceType(), joinedAlias, newPath);
                    if (children.size() == 1 && !splitPoints.contains(eReference)) {
                        String child = children.get(0);
                        nonContained.append(" left outer join fetch " + alias + "." + sf.getName() + " " + joinedAlias + child);
                    } else {
                        for (String child : children) {
                            result.add(" left outer join fetch " + alias + "." + sf.getName() + " " + joinedAlias + child);
                        }
                    }
                }
            }
        }
        if (nonContained.length() > 0) {
            if (result.size() == 0) {
                result.add(nonContained.toString());
            } else {
                int lastIndex = result.size() - 1;
                String last = result.get(lastIndex);
                result.set(lastIndex, nonContained.toString() + last);
            }
        }
        if (result.size() == 0) {
            result.add("");
        }
        return result;
    }

    public static Map getMap(Map entity) {
        String entityType = (String) entity.get("_type_");
        Long e_id = new Long(entity.get("e_id").toString());
        return getMap(entityType, e_id);
    }

    public static Map getMap(String entityType, Long e_id) {
        EClass eClass = findEClass(entityType);
        Map result = new HashMap() {{
            put("_type_", entityType);
            put("e_id", e_id);
        }};
        Object object = Context.getCurrent().getSession().get(entityType, e_id);
        if (object == null) {
            throw new RuntimeException("Entity " + entityType + "#" + e_id + " not found");
        }
        copyEntity((Map) object, result, eClass, true);
        return result;
    }

    private static void copyEntity(Map entity, Map object, EClass eClass, boolean deep) {
        for (EStructuralFeature sf : eClass.getEAllStructuralFeatures()) {
            Object entityValue = entity.get(sf.getName());
            if (entityValue == null)
                continue;
            if (sf instanceof EAttribute && (deep || !isMultiline(sf))) {
                if (sf.isMany()) {
                    List objectValue = new ArrayList();
                    object.put(sf.getName(), objectValue);
                    for (Object value : (List) entityValue) {
                        Object otherValue = decodeAttributeValue((EAttribute) sf, value);
                        objectValue.add(otherValue);
                    }
                } else {
                    Object otherValue = decodeAttributeValue((EAttribute) sf, entityValue);
                    object.put(sf.getName(), otherValue);
                }
            } else if (sf instanceof EReference && deep) {
                EReference eReference = (EReference) sf;
                if (sf.isMany()) {
                    List objectValue = new ArrayList();
                    object.put(sf.getName(), objectValue);
                    for (Object value : (List) entityValue) {
                        Map subObject = copyReferencedObject(eReference, (Map) value);
                        objectValue.add(subObject);
                    }
                } else {
                    Map subObject = copyReferencedObject(eReference, (Map) entityValue);
                    object.put(sf.getName(), subObject);
                }
            }
        }
    }

    private static Map copyReferencedObject(EReference eReference, Map value) {
        Map subObject = new HashMap();
        String refType = (String) value.get("_type_");
        EClass eSubClass = findEClass(refType);
        subObject.put("_type_", refType);
        subObject.put("e_id", value.get("e_id"));
        copyEntity(value, subObject, eSubClass, eReference.isContainment());
        return subObject;
    }

    public static Map readObjectDeep(Map entity) {
        String entityType = (String) entity.get("_type_");
        Long e_id = new Long(entity.get("e_id").toString());
        Map result = readObjectDeep(entityType, e_id);
        return result;
    }

    public static Map readObjectDeep(String entityType, Long e_id) {
        Map result = new HashMap() {{
            put("_type_", entityType);
            put("e_id", e_id);
        }};
        EClass eClass = findEClass(entityType);
        Map persistent = prefetchEntity(entityType, e_id, eClass);
        copyEntity(persistent, result, eClass, true);
        return result;
    }

    public static Map prefetchEntity(Map entity) {
        String entityType = (String) entity.get("_type_");
        Long e_id = new Long(entity.get("e_id").toString());
        return prefetchEntity(e_id, entityType);
    }

    private static Map prefetchEntity(Object e_id, String entityType) {
        return prefetchEntity(entityType, e_id, findEClass(entityType));
    }

    public static Map prefetchEntity(String entityType, Object e_id, EClass eClass) {
        Map entity = null;
        List<String> prefetches = generatePrefetches(eClass, "t", new ArrayList<>());
        String prefix = "from " + entityType + " t";
        for (String prefetch : prefetches) {
            String prefetchSQL = prefix + prefetch + " where t.e_id=" + e_id;
            Query query = Context.getCurrent().getSession().createQuery(prefetchSQL);
            entity = (Map) query.uniqueResult();
        }
        if (entity == null) {
            throw new RuntimeException("Entity " + entityType + "#" + e_id + " not found");
        }
        return entity;
    }

    private static Object[] uniqueResult(String query) {
        Object row = Context.getCurrent().getSession().createQuery(query).uniqueResult();
        if (row instanceof Object[]) {
            return (Object[]) row;
        } else {
            return new Object[]{row};
        }
    }

    public static Map readObjectFast(Map object, EClass eClass) {
        if (eClass == null) {
            eClass = findEClass((String) object.get("_type_"));
        }
        List<String> columns = new ArrayList<>();
        String query = makeRowQuery("e", eClass, columns) + " where e.e_id=" + object.get("e_id");
        Object[] row = uniqueResult(query);
        if (row == null) {
            return null;
        }
        populateObject(object, eClass, columns, row);
        return object;
    }

    private static void populateObject(Map object, EClass eClass, List<String> columns, Object[] row) {
        object.put("_type_", row[0]);
        object.put("e_id", row[1]);
        for (EStructuralFeature sf : eClass.getEAllStructuralFeatures()) {
            if (!sf.isMany()) {
                if (sf instanceof EAttribute) {
                    int index = columns.indexOf(sf.getName());
                    if (index >= 0) {
                        Object value = row[index];
                        if (value != null) {
                            object.put(sf.getName(), decodeAttributeValue((EAttribute) sf, value));
                        }
                    }
                }
                if (sf instanceof EReference) {
                    Map refMap = new HashMap();
                    String prefix = sf.getName() + ".";
                    for (int index = 0; index < columns.size(); ++index) {
                        String column = columns.get(index);
                        if (column.startsWith(prefix)) {
                            String name = column.substring(prefix.length());
                            EAttribute eAttribute = (EAttribute) ((EReference) sf).getEReferenceType().getEStructuralFeature(name);
                            Object value = row[index];
                            if (value != null) {
                                refMap.put(name, decodeAttributeValue(eAttribute, value));
                            }
                        }
                    }
                    if (refMap.get("_type_") != null) {
                        object.put(sf.getName(), refMap);
                    }
                }
            } else {
                object.put(sf.getName(), new ArrayList());
            }
        }
    }

    private static String makeRowQuery(String alias, EClass eClass, List<String> columns) {
        StringBuffer selectList = makeSelectList(alias, eClass, columns);
        StringBuffer joinList = makeJoinList(alias, eClass);
        return "select " + selectList.toString() + " from " + getTypeName(eClass) + " e " + joinList.toString();
    }

    private static boolean isMultiline(EStructuralFeature sf) {
        return "true".equalsIgnoreCase(getAnnotation(sf, "mspace.ui", "multiline", ""));
    }

    private static StringBuffer makeSelectList(String alias, EClass eClass, List<String> columns) {
        StringBuffer selectList = new StringBuffer();
        columns.add("_type_");
        columns.add("e_id");
        selectList.append("type(").append(alias).append("), ").append(alias).append(".e_id");
        for (EStructuralFeature sf : eClass.getEAllStructuralFeatures()) {
            if (!sf.isMany()) {
                selectList.append(", ");
                if (sf instanceof EAttribute) {
                    selectList.append(alias).append(".").append(sf.getName());
                    columns.add(sf.getName());
                } else {
                    EReference rf = (EReference) sf;
                    String refClassName = getTypeName(rf.getEReferenceType());
                    selectList.
                            append("case ").append(sf.getName()).
                            append("_ when null then '").append(refClassName).
                            append("' else type(").append(sf.getName()).append("_) end, ").
                            append(sf.getName()).append("_.e_id");
                    columns.add(sf.getName() + "._type_");
                    columns.add(sf.getName() + ".e_id");
                    EClass refClass = findEClass(refClassName);
                    for (EStructuralFeature att : refClass.getEAllAttributes()) {
                        if (!att.isMany() && (rf.isContainment() || !isMultiline(att))) {
                            columns.add(sf.getName() + "." + att.getName());
                            selectList.append(", ").append(sf.getName()).append("_.").append(att.getName());
                        }
                    }
                }
            }
        }
        return selectList;
    }

    private static StringBuffer makeJoinList(String alias, EClass eClass) {
        StringBuffer joinList = new StringBuffer();
        for (EStructuralFeature sf : eClass.getEAllStructuralFeatures()) {
            if (!sf.isMany() && sf instanceof EReference) {
                joinList.
                        append("left join ").append(alias).append(".").append(sf.getName()).
                        append(" as ").append(sf.getName()).append("_ ");
            }
        }
        return joinList;
    }

    public static List<Map> listFast(String typeName, Map<String, Object> requestParams) {
        List<Map> result = new LinkedList();
        EClass eClass = findEClass(typeName);
        List<String> columns = new ArrayList<>();
        String query = makeRowQuery("e", eClass, columns);
        for (Object row : Database.getNew().makeQuery(query, requestParams, "e").list()) {
            Map object = new HashMap();
            populateObject(object, eClass, columns, ((Object[]) row));
            result.add(object);
        }
        return result;
    }

    private static Date parseDate(Object value) {
        try {
            return jsonDateParser.parse(value.toString());
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private static Date parseDateTime(Object value) {
        try {
            return jsonTimestampParser.parse(value.toString().replaceAll("Z$", "+0000"));
        } catch (Throwable th) {
            return parseDate(value);
        }
    }

    private static Object decodeAttributeValue(EAttribute sf, Object value) {
        if (value instanceof EEnumLiteral) {
            return value.toString();
        }
        if (value instanceof java.sql.Timestamp) {
            return jsonTimestampParser.format(value);
        }
        if (value instanceof java.util.Date) {
            if (sf != null && getAnnotation(sf, "teneo.jpa", "value", "").contains("TIMESTAMP")) {
                return jsonTimestampParser.format(value);
            } else {
                return jsonDateParser.format(value);
            }
        }
        return value;
    }

    private static Object encodeAttributeValue(EAttribute sf, Object value) {
        if (value == null) {
            return null;
        }
        EDataType eDataType = sf.getEAttributeType();
        if (eDataType instanceof EEnum) {
            EEnum eEnum = (EEnum) eDataType;
            return eEnum.getEEnumLiteral(value.toString());
        }
        if (eDataType instanceof EDataType.Internal) {
            EDataType.Internal internal = (EDataType.Internal) eDataType;
            EDataType.Internal.ConversionDelegate delegate = internal.getConversionDelegate();
            if (delegate != null) {
                String strValue = delegate.convertToString(value);
                return delegate.createFromString(strValue);
            }
            Class<?> instanceClass = null;
            try {
                instanceClass = internal.getInstanceClass();
            } catch (Exception e) {
            }
            if (instanceClass != null) {
                if (instanceClass == Date.class) {
                    if (getAnnotation(sf, "teneo.jpa", "value", "").contains("TIMESTAMP")) {
                        return parseDateTime(value);
                    } else {
                        return parseDate(value);
                    }
                }
                if (instanceClass == Timestamp.class)
                    return new Timestamp(parseDateTime(value).getTime());
                if (instanceClass == BigDecimal.class)
                    return new BigDecimal(value.toString());
                if (instanceClass == Long.class) {
                    if (value instanceof Integer) {
                        return new Long((Integer) value);
                    }
                }
            }
        }
        return value;
    }

    static class MergeState {
        List<Map> deleted = new ArrayList<>();
        List<Map> inserted = new ArrayList<>();
        Map<Map, Map> merged = new HashMap<>();
        Map<Map, Map> internals = new HashMap<>();
        Map<String, Map> replaced = new HashMap<>();
        Map<Object, Map> ids = new HashMap<>();
        Boolean isImport = false;

        String getKey(Map entity) {
            return entity.get("_type_").toString() + "#" + entity.get("e_id");
        }

        Map replace(Map entity, Map newEntity) {
            //delete(entity);
            insert(newEntity);
            replaced.put(getKey(entity), newEntity);
            return newEntity;
        }

        void delete(Map entity) {
            deleted.add(entity);
        }

        void insert(Map entity) {
            //Context.getCurrent().getTxSession().save((String)entity.get("_type_"), entity);
            inserted.add(0, entity);
        }

        Map findReference(Map ref) {
            Map internal = internals.get(ref);
            if (internal != null) {
                ref = internal;
            }
            Map result = merged.get(ref);
            if (result == null) {
                result = replaced.get(getKey(ref));
            }
            return result;
        }
    }

    public static Map merge(Map entity, Map other) {
        return merge(entity, other, false);
    }

    public static Map merge(Map entity, Map other, boolean isImport) {
        MergeState state = new MergeState();
        state.isImport = isImport;
        collectIDs(other, state.ids);
        setInternalRefs(other, state.internals, state.ids);
        entity = mergeContainment(entity, other, state);
        mergeNonContainment(entity, other, state);
        for (Map e : state.inserted) {
            Context.getCurrent().getTxSession().save((String) e.get("_type_"), e);
        }
        Context.getCurrent().getTxSession().save((String) entity.get("_type_"), entity);
        //Context.getCurrent().savepoint();
        for (Map e : state.deleted) {
            boolean hasParent = false;
            EClass eClass = findEClass((String) e.get("_type_"));
            for (EReference sf : eClass.getEAllReferences()) {
                if (sf.isContainer() && e.get(sf.getName()) != null) {
                    e.put(sf.getName(), null);
                    hasParent = true;
                }
            }
            if (!hasParent) {
                Context.getCurrent().getTxSession().delete((String) e.get("_type_"), e);
            }
        }
        return entity;
    }

    private static void mergeNonContainment(Map entity, Map other, MergeState state) {
        String entityType = (String) entity.get("_type_");
        EClass eClass = findEClass(entityType);
        for (EReference sf : eClass.getEAllReferences()) {
            if (sf.isContainment()) {
                Object otherObject = other.get(sf.getName());
                if (otherObject == null) {
                    continue;
                }
                Object refObject = entity.get(sf.getName());
                if (!sf.isMany()) {
                    mergeNonContainment(EObjectMap.wrap(refObject), EObjectMap.wrap(otherObject), state);
                } else {
                    List refList = (List) refObject;
                    List otherList = (List) otherObject;
                    for (int i = 0; i < otherList.size(); ++i) {
                        mergeNonContainment(EObjectMap.wrap(refList.get(i)), EObjectMap.wrap(otherList.get(i)), state);
                    }
                }
            } else {
                if (sf.isContainer()) {
                    continue;
                }
                Object otherObject = other.get(sf.getName());
                if (!sf.isMany()) {
                    Map refObject = getRefObject(state, otherObject);
                    entity.put(sf.getName(), refObject);
                } else {
                    List refList = (List) entity.get(sf.getName());
                    refList.clear();
                    List otherList = otherObject == null ? Collections.emptyList() : (List) otherObject;
                    for (int i = 0; i < otherList.size(); ++i) {
                        Map refObject = getRefObject(state, otherList.get(i));
                        if (refObject != null) {
                            refList.add(refObject);
                        }
                    }
                }
            }
        }
    }

    private static Map getRefObject(MergeState state, Object otherObject) {
        if (otherObject == null) {
            return null;
        }
        Map otherMap = EObjectMap.wrap(otherObject);
        Map refObject = state.findReference(otherMap);
        if (refObject != null) {
            return refObject;
        }
        Serializable e_id = (Serializable) otherMap.get("e_id");
        if (e_id == null) {
            throw new RuntimeException("Merged object not found: " + otherObject.toString());
        }
        refObject = EObjectMap.wrap(Context.getCurrent().getSession().get((String) otherMap.get("_type_"), new Long(e_id.toString())));
        if (refObject == null) {
            throw new RuntimeException("Referenced object not found: " + otherMap.toString());
        }
        return refObject;
    }

    private static Map mergeContainment(Map entity, Map other, MergeState state) {
        if (other == null) {
            if (entity != null) {
                state.delete(entity);
            }
            return null;
        }
        String otherType = (String) other.get("_type_");
        if (entity == null) {
            Serializable e_id = (Serializable) other.get("e_id");
            if (e_id == null) {
                entity = instantiate(otherType);
                state.insert(entity);
            } else {
                entity = EObjectMap.wrap(Context.getCurrent().getSession().get(otherType, new Long(e_id.toString())));
                if (entity == null) {
                    throw new IllegalArgumentException("Entity with id " + e_id + " not found");
                }
            }
            entity = mergeContainment(entity, other, state);
            return entity;
        }
        String entityType = (String) entity.get("_type_");
        if (!entityType.equals(otherType)) {
            entity = state.replace(entity, instantiate(otherType));
            entity = mergeContainment(entity, other, state);
            return entity;
        }
        //Map otherMerged = state.merged.get(other);
        //if (otherMerged != null) {
        //    return otherMerged;
        //}
        state.merged.put(other, entity);

        EClass eClass = findEClass(entityType);
        for (EAttribute sf : eClass.getEAllAttributes()) {
            if (!sf.isMany()) {
                Object otherValue = other.get(sf.getName());
                if (!(other instanceof EObjectMap)) {
                    otherValue = encodeAttributeValue(sf, otherValue);
                }
                entity.put(sf.getName(), otherValue);
            } else {
                List valueList = (List) entity.get(sf.getName());
                valueList.clear();
                List otherValueList = (List) other.get(sf.getName());
                if (otherValueList != null) {
                    for (Object otherValue : otherValueList) {
                        if (!(other instanceof EObjectMap)) {
                            otherValue = encodeAttributeValue(sf, otherValue);
                        }
                        valueList.add(otherValue);
                    }
                }
            }
        }
        for (EReference sf : eClass.getEAllReferences()) {
            if (sf.isContainment()) {
                Object otherObject = other.get(sf.getName());
                Object refObject = entity.get(sf.getName());
                if (!sf.isMany()) {
                    Map refMap = EObjectMap.wrap(refObject);
                    Map otherMap = EObjectMap.wrap(otherObject);
                    if (state.isImport && sf.getName().equals("auditInfo") && otherMap.get("_type_").equals("auth.AuditInfo")) {
                        otherMap.put("changeDateTime", null);
                    }
                    Map mergedMap = mergeContainment(refMap, otherMap, state);
                    if (refMap != mergedMap) {
                        entity.put(sf.getName(), mergedMap);
                    }
                } else {
                    List refList = (List) refObject;
                    List otherList = otherObject == null ? Collections.emptyList() : (List) otherObject;
                    for (int i = 0; i < otherList.size(); ++i) {
                        Map refMap = null;
                        if (i < refList.size()) {
                            refMap = EObjectMap.wrap(refList.get(i));
                        }
                        Map otherMap = EObjectMap.wrap(otherList.get(i));
                        Map mergedMap = mergeContainment(refMap, otherMap, state);
                        if (i < refList.size()) {
                            if (mergedMap != refMap) {
                                Object ref = refList.remove(i);
                                if (ref != null) {
                                    state.delete(EObjectMap.wrap(ref));
                                }
                                refList.add(i, mergedMap);
                            }
                        } else {
                            refList.add(mergedMap);
                        }
                    }
                    while (refList.size() > otherList.size()) {
                        Object ref = refList.remove(refList.size() - 1);
                        state.delete(EObjectMap.wrap(ref));
                    }
                }
            }
        }
        return entity;
    }

    interface Visitor {
        boolean visit(Map parent, EReference sf, int index, Map entity, Object cookie);
    }

    public static void visitContained(Map entity, Object state, Visitor visitor) {
        EClass eClass = findEClass((String) entity.get("_type_"));
        for (EReference sf : eClass.getEAllReferences()) {
            if (sf.isContainment()) {
                if (!sf.isMany()) {
                    Map refMap = EObjectMap.wrap(entity.get(sf.getName()));
                    if (refMap != null) {
                        if (visitor.visit(entity, sf, -1, refMap, state)) {
                            visitContained(refMap, state, visitor);
                        }
                    }
                } else {
                    List refList = (List) entity.get(sf.getName());
                    if (refList != null) {
                        for (int i = 0; i < refList.size(); ++i) {
                            Map refMap = EObjectMap.wrap(refList.get(i));
                            if (refMap != null) {
                                if (visitor.visit(entity, sf, i, refMap, state)) {
                                    visitContained(refMap, state, visitor);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static void visitNonContained(Map entity, Object state, Visitor visitor) {
        visitContained(entity, state, new Visitor() {
            @Override
            public boolean visit(Map parent, EReference sf, int index, Map entity, Object cookie) {
                boolean cont = true;
                EClass eClass = findEClass((String) entity.get("_type_"));
                for (EReference ref : eClass.getEAllReferences()) {
                    if (!ref.isContainment() && !ref.isContainer()) {
                        if (!ref.isMany()) {
                            Map refMap = EObjectMap.wrap(entity.get(ref.getName()));
                            if (refMap != null) {
                                cont = visitor.visit(entity, ref, -1, refMap, state) && cont;
                            }
                        } else {
                            List refList = (List) entity.get(ref.getName());
                            if (refList != null) {
                                for (int i = 0; i < refList.size(); ++i) {
                                    Map refMap = EObjectMap.wrap(refList.get(i));
                                    if (refMap != null) {
                                        cont = visitor.visit(entity, ref, i, refMap, state) && cont;
                                    }
                                }
                            }
                        }
                    }
                }
                return cont;
            }
        });

    }

    public static void collectIDs(Map entity, Map<Object, Map> ids) {
        visitContained(entity, ids, new Visitor() {
            @Override
            public boolean visit(Map parent, EReference sf, int index, Map entity, Object cookie) {
                Map<Object, Map> ids = (Map<Object, Map>) cookie;
                Object e_id = entity.get("e_id");
                if (e_id != null) {
                    ids.put(e_id, entity);
                    entity.remove("e_id");
                }
                return true;
            }
        });
    }

    public static void setInternalRefs(Map entity, Map<Map, Map> internals, Map<Object, Map> ids) {
        visitNonContained(entity, ids, new Visitor() {
            @Override
            public boolean visit(Map parent, EReference sf, int index, Map entity, Object cookie) {
                Map<Object, Map> ids = (Map<Object, Map>) cookie;
                Object e_id = entity.get("e_id");
                if (e_id != null) {
                    Map refEntity = ids.get(e_id);
                    if (refEntity != null) {
                        internals.put(entity, refEntity);
                    }
                }
                return true;
            }
        });
    }
}
