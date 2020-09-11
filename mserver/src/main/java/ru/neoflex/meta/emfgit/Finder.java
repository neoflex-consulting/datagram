package ru.neoflex.meta.emfgit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.*;

import static java.lang.Integer.min;

public class Finder {
    private String warning;
    private int skip = 0;
    private int limit = -1;
    private int idsLoaded = 0;
    private long idsLoadedMs = 0;
    private int resLoaded = 0;
    private long resLoadedMs = 0;
    private ResourceSet resourceSet;
    private ObjectNode selector = new ObjectMapper().createObjectNode();

    public static Finder create() {
        return new Finder();
    }

    public static Finder create(EClass eClass) {
        Finder finder = create();
        finder.selector().with("contents").put("eClass", EcoreUtil.getURI(eClass).toString());
        return finder;
    }

    public static Finder create(EClass eClass, Map<String, String> attributes) {
        Finder finder = create(eClass);
        for (String key: attributes.keySet()) {
            finder.selector().with("contents").put(key, attributes.get(key));
        }
        return finder;
    }

    public Finder skip(int value) {
        skip = value;
        return this;
    }

    public Finder limit(int value) {
        limit = value;
        return this;
    }


    public Finder execute(Transaction tx) throws IOException {
        long startTime = System.currentTimeMillis();
        Database database = tx.getDatabase();
        List<EntityId> ids = findIds(selector, tx);
        long idsLoadedTime = System.currentTimeMillis();
        idsLoaded = ids.size();
        resourceSet = database.createResourceSet(tx);
        int startIndex = skip <= 0 ? 0 : min(skip, ids.size());
        int length = limit <= 0 ? ids.size() - startIndex : min(limit, ids.size() - startIndex);
        for (EntityId entityId: ids.subList(startIndex, startIndex + length)) {
            Entity entity = tx.load(entityId);
            Resource resource = database.createResourceSet(tx).createResource(database.createURI(entity.getId(), entity.getRev()));
            database.loadResource(entity.getContent(), resource);
            if (match(entity, resource.getContents().get(0), selector)) {
                resourceSet.getResources().add(resource);
            }
        }
        resLoaded = length;
        long resLoadedTime = System.currentTimeMillis();
        idsLoadedMs = idsLoadedTime - startTime;
        resLoadedMs = resLoadedTime - idsLoadedTime;
        return this;
    }

    private boolean match(EntityId entityId, EObject object, ObjectNode query) throws IOException {
        JsonNode _id = query.get("id");
        if (_id != null && !Objects.equals(_id.textValue(), entityId.getId())) {
            return false;
        }
        JsonNode _rev = query.get("rev");
        if (_rev != null && !Objects.equals(_rev.textValue(), entityId.getRev())) {
            return false;
        }
        JsonNode contents = query.get("contents");
        if (contents != null) {
            return matchNodes(object, contents);
        }
        return true;
    }

    private boolean matchOp(String op, Object object, JsonNode query) {
        if ("$and".equals(op)) {
            return matchAnd(object, query);
        }
        if ("$or".equals(op)) {
            return matchOr(object, query);
        }
        if ("$not".equals(op)) {
            return matchNot(object, query);
        }
        if ("$nor".equals(op)) {
            return matchNor(object, query);
        }
        if ("$all".equals(op)) {
            return matchAll(object, query);
        }
        if ("$elemMatch".equals(op)) {
            return matchElemMatch(object, query);
        }
        if ("$allMatch".equals(op)) {
            return matchAllMatch(object, query);
        }
        if ("$lt".equals(op)) {
            return matchLt(object, query);
        }
        if ("$gt".equals(op)) {
            return matchGt(object, query);
        }
        if ("$lte".equals(op)) {
            return matchLe(object, query);
        }
        if ("$gte".equals(op)) {
            return matchGe(object, query);
        }
        if ("$eq".equals(op)) {
            return object.toString().equals(query.asText());
        }
        if ("$ne".equals(op)) {
            return !object.equals(query);
        }
        if ("$exists".equals(op)) {
            return query.asBoolean() == (object != null);
        }
        if ("$size".equals(op)) {
            return matchSize(object, query);
        }
        if ("$type".equals(op)) {
            return query.asText().equals(object.getClass().getSimpleName());
        }
        if ("$in".equals(op)) {
            return matchIn(object, query);
        }
        if ("$nin".equals(op)) {
            return !matchIn(object, query);
        }
        if ("$regex".equals(op)) {
            return object.toString().matches(query.asText());
        }
        return false;
    }

    private int compare(Object object, JsonNode query) {
        if (object instanceof Number && query.isNumber()) {
            return new BigDecimal(object.toString()).compareTo(query.decimalValue());
        }
        if (object instanceof String && query.isTextual()) {
            return ((String) object).compareTo(query.asText());
        }
        if (object instanceof Boolean && query.isBoolean()) {
            return new Boolean((Boolean) object).compareTo(query.asBoolean());
        }
        throw new IllegalArgumentException("Can't compare values: " + object.toString() + ", " + query.toString());
    }

    private boolean matchSize(Object object, JsonNode query) {
        return object instanceof List && ((List) object).size() == query.asInt();
    }
    private boolean matchLt(Object object, JsonNode query) {
        return compare(object, query) < 0;
    }

    private boolean matchLe(Object object, JsonNode query) {
        return compare(object, query) <= 0;
    }

    private boolean matchGt(Object object, JsonNode query) {
        return compare(object, query) > 0;
    }

    private boolean matchGe(Object object, JsonNode query) {
        return compare(object, query) >= 0;
    }

    private boolean matchElemMatch(Object object, JsonNode query) {
        if (!(object instanceof List)) {
            return false;
        }
        for (Object objectNode: (List) object) {
            if (matchNodes(objectNode, query)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchAllMatch(Object object, JsonNode query) {
        if (!(object instanceof List)) {
            return false;
        }
        for (Object objectNode: (List) object) {
            if (!matchNodes(objectNode, query)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchNot(Object object, JsonNode query) {
        return !matchNodes(object, query);
    }

    private boolean matchAll(Object object, JsonNode query) {
        if (!(object instanceof List)) {
            return false;
        }
        if (!query.isArray()) {
            return false;
        }
        for (JsonNode queryNode: query) {
            boolean found = false;
            for (Object objectNode: (List) object) {
                if (objectNode.equals(queryNode)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private boolean matchNor(Object object, JsonNode query) {
        if (!query.isArray()) {
            return false;
        }
        for (JsonNode node: query) {
            if (matchNodes(object, node)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchIn(Object object, JsonNode query) {
        if (!query.isArray()) {
            return false;
        }
        for (JsonNode node: query) {
            if (object.toString().equals(node.asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchNodes(Object object, JsonNode query) {
        if (object == null) {
            return query == null || query.isNull();
        }
        if (query == null) {
            return true;
        }
        if (object instanceof EObject || object instanceof List) {
            if (!query.isObject()) {
                return false;
            }
            return matchFields(object, query);
        }
        return matchPlain(object, query);
    }

    private boolean matchPlain(Object object, JsonNode query) {
        if (query.isObject()) {
            return matchFields(object, query);
        }
        return object.toString().equals(query.asText());
    }

    private boolean matchAnd(Object object, JsonNode query) {
        if (!query.isArray()) {
            return false;
        }
        for (JsonNode node: query) {
            if (!matchNodes(object, node)) {
                return false;
            }
        }
        return true;
    }

    private Object getField(Object object, String name) {
        if (object instanceof EObject) {
            EObject eObject = (EObject) object;
            EClass eClass = eObject.eClass();
            EStructuralFeature sf = eClass.getEStructuralFeature(name);
            if (sf != null) {
                return eObject.eGet(sf);
            }
        }
        return null;
    }

    private boolean matchFields(Object object, JsonNode query) {
        if (!query.isObject()) {
            return false;
        }
        for (Iterator<String> it = query.fieldNames(); it.hasNext();) {
            String fieldName = it.next();
            JsonNode queryNode = query.get(fieldName);
            if (fieldName.startsWith("$")) {
                if (!matchOp(fieldName, object, queryNode)) {
                    return false;
                }
                continue;
            }
            if (fieldName.equals("eClass")) {
                if (!EcoreUtil.getURI(((EObject) object).eClass()).toString().equals(queryNode.asText())) {
                    return false;
                }
                continue;
            }
            Object objectNode = getField(object, unescape(fieldName));
            if (!matchNodes(objectNode, queryNode)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchOr(Object object, JsonNode query) {
        if (!query.isArray()) {
            return false;
        }
        for (JsonNode node: query) {
            if (matchNodes(object, node)) {
                return true;
            }
        }
        return false;
    }

    private String unescape(String fieldName) {
        return fieldName.replaceAll("^[\\\\]\\$", "\\$");
    }

    private List<EntityId> findIds(ObjectNode query, Transaction tx) throws IOException {
        Database database = tx.getDatabase();
        if (query.has("id")) {
            String id = query.get("id").asText();
            EntityId entityId = new EntityId(id, null);
            if (Files.exists(tx.getIdPath(entityId))) {
                return Collections.singletonList(entityId);
            }
            return Collections.emptyList();
        }
        JsonNode contents = query.get("contents");
        if (contents != null && contents instanceof ObjectNode) {
            JsonNode classURI = contents.get("eClass");
            if (classURI != null) {
                ResourceSet resourceSet = database.createResourceSet(tx);
                EClass eClass = (EClass) resourceSet.getEObject(URI.createURI(classURI.asText()), false);
                if (eClass != null) {
                    String name = null;
                    EStructuralFeature nameSF = database.getQNameFeature(eClass);
                    if (nameSF != null) {
                        JsonNode nameNode = contents.get(nameSF.getName());
                        if (nameNode != null) {
                            name = nameNode.asText();
                        }
                    }
                    List<IndexEntry> ieList = database.findEClassIndexEntries(eClass, name, tx);
                    List<EntityId> result = new ArrayList<>();
                    for (IndexEntry ie: ieList) {
                        result.add(new EntityId(new String(ie.getContent()), null));
                    }
                    return result;
                }
            }
        }
        warning = "No index used";
        return tx.all();
    }

    public String getWarning() {
        return warning;
    }

    public ResourceSet getResourceSet() throws IOException {
        return resourceSet;
    }

    public ObjectNode selector() {
        return selector;
    }

    public Finder selector(ObjectNode selector) {
        this.selector = selector;
        return this;
    }

    public ObjectNode getExecutionStats() {
        ObjectNode executionStats = new ObjectMapper().createObjectNode();
        executionStats.put("idsLoaded", idsLoaded);
        executionStats.put("idsLoadedMs", idsLoadedMs);
        executionStats.put("resLoaded", resLoaded);
        executionStats.put("resLoadedMs", resLoadedMs);
        return executionStats;
    }
}
