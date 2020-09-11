package ru.neoflex.meta.utils;

import org.hibernate.Query;
import org.hibernate.Session;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by orlov on 26.04.2015.
 */
public class MetaHelper {

    public static Document createMappingDocument(Session session) throws ParserConfigurationException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.newDocument();
        Element rootElement = doc.createElement("hibernate-mapping");
        doc.appendChild(rootElement);

        MetaHelper.createClasses(session, doc, rootElement, null);
        return doc;
    }

    private static void createClasses(Session session, Document doc, Element rootElement, Map parentClass) {
        String sql = "FROM  MetaPersistentEntity";
        if (parentClass == null) {
            sql += " WHERE parent IS NULL";
        }
        else {
            sql += " WHERE parent.id=:parent";
        }
        Query eQuery = session.createQuery(sql);
        if (parentClass != null) {
            eQuery.setParameter("parent", parentClass.get("id"));
        }
        List<Map> entities = eQuery.list();
        for (Map entity: entities) {
            Element classElement = createClassElement(doc, entity);
            rootElement.appendChild(classElement);
            createClasses(session, doc, rootElement, entity);
        }
    }

    private static void setAttribute(Element e, String key, Object value, Object value2) {
        if (value != null) {
            e.setAttribute(key, value.toString());
        }
        else if (value2 != null) {
            e.setAttribute(key, value2.toString());
        }
    }

    private static void setAttribute(Element e, String key, Object value) {
        setAttribute(e, key, value, null);
    }

    private static Element createClassElement(Document doc, Map entity) {
        Map parent = (Map) entity.get("parent");
        Element classElement = parent == null ? doc.createElement("class") : doc.createElement("joined-subclass");
        setAttribute(classElement, "entity-name", entity.get("code"));
        setAttribute(classElement, "table", entity.get("code"));
        if (parent != null) {
            setAttribute(classElement, "extends", parent.get("code"));
        }
        Element tuplizerElement = doc.createElement("tuplizer");
        tuplizerElement.setAttribute("entity-mode", "dynamic-map");
        tuplizerElement.setAttribute("class", "org.hibernate.tuple.entity.CustomMapTuplizerImpl");
        classElement.appendChild(tuplizerElement);
        Map keyAttribute = createIdElement(doc, entity, parent, classElement);
        for (Map property: (Collection<Map>)entity.get("properties")) {
            if (property == null)
                continue;
            if (keyAttribute != null && keyAttribute.get("code").equals(property.get("code")))
                continue;
            createPropertyElement(doc, classElement, property);
        }
        for (Map property : (Collection<Map>)entity.get("collections")) {
            createCollectionElement(doc, classElement, property);
        }
        return classElement;
    }

    private static Map createIdElement(Document doc, Map entity, Map parent, Element classElement) {
        Map keyAttribute = getKeyAttribute(entity);
        if (parent != null) {
            Element keyElement = doc.createElement("key");
            if (keyAttribute != null) {
                keyElement.setAttribute("column", (String) keyAttribute.get("code"));
            }
            else {
                keyElement.setAttribute("column", "id");
            }
            classElement.appendChild(keyElement);
        }
        else {
            Element idElement = doc.createElement("id");
            if (keyAttribute == null) {
                idElement.setAttribute("name", "id");
                idElement.setAttribute("column", "id");
                idElement.setAttribute("type", "string");
                idElement.setAttribute("length", "40");
                Element generatorElement = doc.createElement("generator");
                generatorElement.setAttribute("class", "uuid2");
                idElement.appendChild(generatorElement);
            }
            else {
                idElement.setAttribute("name", (String) keyAttribute.get("code"));
                idElement.setAttribute("column", (String) keyAttribute.get("code"));
                idElement.setAttribute("type", (String) keyAttribute.get("attTypeCode"));
                idElement.setAttribute("length", String.valueOf(keyAttribute.get("attLength")));
                Element generatorElement = doc.createElement("generator");
                generatorElement.setAttribute("class", "assigned");
                idElement.appendChild(generatorElement);
            }
            classElement.appendChild(idElement);
        }
        return keyAttribute;
    }

    public static Map getKeyAttribute(Map entity) {
        Map keyAttribute = null;
        Map p = entity;
        while (p != null && keyAttribute == null) {
            for (Map property: (Collection<Map>) entity.get("properties")) {
                if (property != null) {
                    Object isPK  = property.get("isPK");
                    if (isPK != null && ((Boolean) isPK)) {
                        keyAttribute = property;
                        break;
                    }
                }
            }
            p = (Map) p.get("parent");
        }
        return keyAttribute;
    }

    private static void createPropertyElement(Document doc, Element classElement, Map property) {
        String propTypeCode = (String) property.get("$type$");
        if ("MetaAttribute".equals(propTypeCode)) {
            createAttributeElement(doc, classElement, property);
        }
        else if ("MetaRelation".equals(propTypeCode)) {
            createRelationElement(doc, classElement, property);
        }
    }

    private static void createRelationElement(Document doc, Element classElement, Map property) {
        Element m2oElement = doc.createElement("many-to-one");
        setAttribute(m2oElement, "name", property.get("code"));
        Map relatedEntity = (Map)property.get("related");
        setAttribute(m2oElement, "entity-name", relatedEntity.get("code"));
        setAttribute(m2oElement, "column", property.get("code"));
        classElement.appendChild(m2oElement);
    }

    private static void createAttributeElement(Document doc, Element classElement, Map property) {
        Element propertyElement = doc.createElement("property");
        setAttribute(propertyElement, "name", property.get("code"));
        setAttribute(propertyElement, "type", property.get("attTypeCode"));
        setAttribute(propertyElement, "length", property.get("attLength"));
        setAttribute(propertyElement, "column", property.get("code"));
        setAttribute(propertyElement, "precision", property.get("attPrec"));
        setAttribute(propertyElement, "scale", property.get("attScale"));
        setAttribute(propertyElement, "unique", property.get("isUnique"));
        setAttribute(propertyElement, "not-null", property.get("isNotNull"));
        classElement.appendChild(propertyElement);
    }

    private static void createCollectionElement(Document doc, Element classElement, Map collection) {
        String collectionType = (String) collection.get("collectionType");
        Element setElement = doc.createElement(collectionType);
        setAttribute(setElement, "name", collection.get("code"));
        setAttribute(setElement, "inverse", "true");
        Map relatedEntity = (Map) collection.get("related");
        Boolean many2many = (Boolean) collection.get("many2many");
        if ("list".equals(collectionType)) {
            Element indexElement = doc.createElement("list-index");
            Map positionAttribute = (Map) collection.get("positionAttribute");
            setAttribute(indexElement, "column", positionAttribute.get("code"));
            setElement.appendChild(indexElement);
        }
        else if ("map".equals(collectionType)) {
            Element mapKeyElement = doc.createElement("map-key");
            Map positionAttribute = (Map) collection.get("positionAttribute");
            setAttribute(mapKeyElement, "column", positionAttribute.get("code"));
            setAttribute(mapKeyElement, "type", positionAttribute.get("attTypeCode"));
            setElement.appendChild(mapKeyElement);
        }
        if (many2many != null && many2many) {
            setAttribute(setElement, "table", collection.get("relatedTable"));
            Element keyElement = doc.createElement("key");
            setAttribute(keyElement, "column", collection.get("relatedCode"));
            setElement.appendChild(keyElement);
            Element m2mElement = doc.createElement("many-to-many");
            setAttribute(m2mElement, "entity-name", relatedEntity.get("code"));
            setAttribute(m2mElement, "column", collection.get("inverseCode"));
            setElement.appendChild(m2mElement);
        }
        else {
            Map relation = (Map) collection.get("relation");
            Element keyElement = doc.createElement("key");
            setAttribute(keyElement, "column", relation.get("code"));
            setElement.appendChild(keyElement);
            Element o2mElement = doc.createElement("one-to-many");
            setAttribute(o2mElement, "entity-name", relatedEntity.get("code"));
            setElement.appendChild(o2mElement);
        }
        classElement.appendChild(setElement);
    }

}
