package ru.neoflex.meta.utils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.UnhandledException;
import org.apache.commons.lang.exception.NestableRuntimeException;
import org.apache.commons.lang.text.StrBuilder;
import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.emf.ecore.impl.DynamicEObjectImpl;
import org.eclipse.emf.teneo.hibernate.mapping.DynamicENumUserType;
import org.eclipse.emf.teneo.hibernate.mapping.DynamicENumUserTypeProxy;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.engine.profile.Association;
import org.hibernate.engine.spi.*;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.metadata.CollectionMetadata;
import org.hibernate.persister.collection.AbstractCollectionPersister;
import org.hibernate.persister.collection.QueryableCollection;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.persister.entity.Loadable;
import org.hibernate.persister.entity.SingleTableEntityPersister;
import org.hibernate.tuple.entity.EntityMetamodel;
import org.hibernate.tuple.entity.EntityTuplizer;
import org.hibernate.tuple.entity.VersionProperty;
import org.hibernate.type.*;
import org.hibernate.usertype.UserType;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by orlov on 04.05.2015.
 */
public class JSONHelper {
    private static SimpleDateFormat jsonDateParser = new SimpleDateFormat("yyyy-MM-dd");
    private static SimpleDateFormat jsonTimestampParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    //Mon, 12 Oct 2015 10:28:01 GMT
    private static SimpleDateFormat jsonTimestampFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    {
        jsonTimestampFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public static Map fromJSON(String dbType, String entity, Map obj) {
        return fromJSON(Context.getCurrent().getSession(dbType, false), entity, obj);
    }

    public static List fromJSON(Session session, String entity, List list) {
        List result = new LinkedList();
        for (Object element : list) {
            if (element != null) {
                result.add(fromJSON(session, entity, (Map) element));
            }
        }
        return result;
    }

    private static String getCollectionKeyColumn(SessionFactoryImplementor sessionFactory, String role) {
        CollectionMetadata cm = sessionFactory.getCollectionMetadata(role);
        if (!(cm instanceof AbstractCollectionPersister)) {
            return null;
        }
        String[] keyColumns = ((AbstractCollectionPersister) cm).getKeyColumnNames();
        if (keyColumns == null || keyColumns.length == 0) {
            return null;
        }
        return keyColumns[0];
    }

    private static String getPropertyColumn(EntityPersister persister, int propertyIndex) {
        if (!(persister instanceof Loadable)) {
            return null;
        }
        String[] propertyColumns = ((Loadable) persister).getPropertyColumnNames(propertyIndex);
        if (propertyColumns == null || propertyColumns.length == 0) {
            return null;
        }
        return propertyColumns[0];
    }

    private static String getCollectionIndexColumn(SessionFactoryImplementor sessionFactory, String role) {
        CollectionMetadata cm = sessionFactory.getCollectionMetadata(role);
        if (!(cm instanceof AbstractCollectionPersister)) {
            return null;
        }
        String[] indexColumnNames = ((AbstractCollectionPersister) cm).getIndexColumnNames();
        if (indexColumnNames == null || indexColumnNames.length == 0) {
            return null;
        }
        return indexColumnNames[0];
    }

    public static Map fromJSON(Session session, String entity, Map obj) {
        String type = obj.containsKey("_type_") ? (String) obj.get("_type_") : entity;
        SessionFactoryImplementor sf = (SessionFactoryImplementor) session.getSessionFactory();
        EntityPersister ep = sf.getEntityPersister(type);
        EntityMetamodel emm = ep.getEntityMetamodel();
        EntityTuplizer et = ep.getEntityTuplizer();
        ClassMetadata metadata = sf.getClassMetadata(type);
        String idName = metadata.getIdentifierPropertyName();
        Map result = null;
        if (obj.containsKey(idName)) {
            result = (Map) et.instantiate(new Long((Integer) obj.get(idName)), (SessionImplementor) session);
        } else {
            result = (Map) et.instantiate();
        }
        if (emm.isVersioned()) {
            VersionProperty versionProperty = emm.getVersionProperty();
            if (versionProperty != null) {
                Object version = obj.get(versionProperty.getName());
                if (version != null) {
                    obj.remove(versionProperty.getName());
                    et.setPropertyValue(result, emm.getVersionPropertyIndex(), version);
                }
            }
        }
        String[] propNames = metadata.getPropertyNames();
        for (int i = 0; i < propNames.length; ++i) {
            Type propType = metadata.getPropertyType(propNames[i]);
            if (!obj.containsKey(propNames[i])) {
                continue;
            }
            Object value = obj.get(propNames[i]);
            if (value != null) {
                Object newValue = convertValue(session, result, propType, value);
                if (newValue != null) {
                    result.put(propNames[i], newValue);
                }
            }
        }
        return result;
    }

    public static Object convertValue(Session session, Map result, Type propType, Object value) {
        SessionFactoryImplementor sf = (SessionFactoryImplementor) session.getSessionFactory();
        Object newValue = null;
        if (propType instanceof BigDecimalType) {
            newValue = new BigDecimal(String.valueOf(value));
        }
        if (propType instanceof TimestampType) {
            if (value instanceof String) {
                try {
                    newValue = parseTimestamp((String) value);
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }

            } else {
                newValue = new java.util.Date(Long.parseLong(String.valueOf(value)));
            }
        } else if (propType instanceof IntegerType) {
            newValue = new Integer(String.valueOf(value));
        } else if (propType instanceof LongType) {
            newValue = new Long(String.valueOf(value));
        } else if (propType instanceof DoubleType) {
            newValue = new Double(String.valueOf(value));
        } else if (propType instanceof BigDecimalType) {
            newValue = new BigDecimal(String.valueOf(value));
        } else if (propType instanceof CustomType) {
            CustomType customType = (CustomType) propType;
            UserType userType = customType.getUserType();
            if (userType instanceof DynamicENumUserType) {
                newValue = getEnumerator(value, userType);
            } else {
                newValue = customType.fromStringValue(String.valueOf(value));
            }
        } else if (propType instanceof DateType) {
            try {
                newValue = jsonDateParser.parse(String.valueOf(value));
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        } else if (propType instanceof SetType && value instanceof List) {
            SetType setType = (SetType) propType;
            String assocEntity = setType.getAssociatedEntityName(sf);
            List values = fromJSON(session, assocEntity, (List) value);
            newValue = new HashSet(values);
        } else if (propType instanceof EntityType && value instanceof Map) {
            EntityType eType = (EntityType) propType;
            newValue = fromJSON(session, eType.getAssociatedEntityName(), (Map) value);
        } else if (propType instanceof AnyType && value instanceof Map) {
            //AnyType eType = (AnyType) propType;
            //newValue = fromJSON(session, eType.getAssociatedEntityName(sf), (Map) value);
            newValue = session.get((String)((Map) value).get("_type_"), ((Integer)((Map) value).get("e_id")).longValue());
        } else if (propType instanceof ListType && value instanceof List) {
            ListType listType = (ListType) propType;
            Type elementType = listType.getElementType(sf);
            if (elementType.isEntityType()) {
                String assocEntity = listType.getAssociatedEntityName(sf);

                List values = fromJSON(session, assocEntity, (List) value);
                if (result != null) {
                    String keyColumn = getCollectionKeyColumn(sf, listType.getRole());
                    if (keyColumn != null) {
                        for (Object element : values) {
                            if (!((Map) element).containsKey(keyColumn))
                                break;
                            ((Map) element).put(keyColumn, result);
                        }
                    }
                }
                newValue = values;
            } else {
                Hibernate.initialize(value);
                newValue = value;
            }
        } else if (propType instanceof MapType && value instanceof Map) {
            MapType mapType = (MapType) propType;
            String assocEntity = mapType.getAssociatedEntityName(sf);
            Map newMap = new HashMap();
            Map oldMap = (Map) value;
            if (result != null) {
                String keyColumn = getCollectionKeyColumn(sf, mapType.getRole());
                for (Object key : oldMap.keySet()) {
                    Map element = fromJSON(session, assocEntity, (Map) oldMap.get(key));
                    if (keyColumn != null) {
                        if (!(element).containsKey(keyColumn))
                            break;
                        element.put(keyColumn, result);
                    }
                    newMap.put(key, element);
                }
            }
            newValue = newMap;
        } else {
            if (!(value instanceof List)) {
                newValue = value;
            }
        }
        return newValue;
    }

    public static Enumerator getEnumerator(Object value, UserType userType) {
        if (value == null) {
            return null;
        }
        return (new DynamicENumUserTypeProxy((DynamicENumUserType) userType)).getEnumerator(String.valueOf(value));
    }

    public static Enumerator getEnumerator(String dbType, String entityType, String propName, String value) {
        Session session = Context.getCurrent().getSession(dbType, false);
        SessionFactoryImplementor sf = (SessionFactoryImplementor) session.getSessionFactory();
        ClassMetadata metadata = sf.getClassMetadata(entityType);
        Type propType = metadata.getPropertyType(propName);
        CustomType customType = (CustomType) propType;
        UserType userType = customType.getUserType();
        return getEnumerator(value, userType);
    }

    public static Map<String, Object> string2map(String s) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(s, new TypeReference<Map<String, Object>>() {
        });
    }

    public static Timestamp parseTimestamp(String ts) throws ParseException {
        if (ts == null)
            return null;
        try {
            return new Timestamp(jsonTimestampParser.parse(ts.replaceAll("Z$", "+0000")).getTime());
        } catch (Exception e) {
            return new Timestamp(new Date().getTime());// jsonTimestampParser2.parse(ts);
        }
    }    

    public static Date parseMillisec(BigInteger ms) throws ParseException {
        if (ms == null)
            return null;

        Calendar c = Calendar.getInstance();
        c.setTime(new Date());
        c.set(Calendar.YEAR, 1970);
        c.set(1970, 0, 1, 0, 0, 0);

        MathContext mc = new MathContext(2, RoundingMode.DOWN);
        MathContext mc1 = new MathContext(2, RoundingMode.CEILING);

        BigDecimal val = BigDecimal.valueOf(ms.longValue());
        BigDecimal s = val.divide(BigDecimal.valueOf(1000), mc1);
        BigDecimal m = s.divide(BigDecimal.valueOf(60), mc1);
        BigDecimal h = m.divide(BigDecimal.valueOf(60), mc1);
        BigDecimal d = h.divide(BigDecimal.valueOf(24), mc1);

        int days = d.round(mc).intValue();

        int milliSeconds = ms.subtract(BigInteger.valueOf((days * 1000 * 60 * 60 * 24))).intValue();

        c.add(Calendar.DATE, days);
        c.add(Calendar.MILLISECOND, milliSeconds);
        return c.getTime();
    }

    public static Collection toJSON(String dbtype, String entityType, Collection collection) {
        return toJSON(dbtype, entityType, collection, TraversalStrategy.DEFAULT);
    }

    public static Collection toJSON(String dbtype, String entityType, Collection collection, TraversalStrategy ts) {
        List result = new LinkedList();
        for (Object entity : collection) {
            if (entity instanceof Map) {
                result.add(toJSON(dbtype, entityType, (Map) entity, ts));
            } else {
                Hibernate.initialize(entity);
                result.add(entity);
            }

        }
        return result;

    }

    private static String getEntityName(String dbtype, Object entity, String defaultName) {
        if (entity instanceof Map) {
            Map map = (Map) entity;
            if (map.containsKey("$type$")) {
                return (String) map.get("$type$");
            }
        }
        if (entity instanceof DynamicEObjectImpl) {
            DynamicEObjectImpl dynamicEObject = (DynamicEObjectImpl) entity;
            return Context.getCurrent().getContextSvc().getTeneoSvc().getHbds().toEntityName(dynamicEObject.eClass());
        }
        return defaultName;
    }

    public static Map toJSON(String dbtype, String entityName, Map object) {
        return toJSON(dbtype, entityName, object, TraversalStrategy.DEFAULT);
    }

    private static boolean isCascadeDelete(EntityPersister entityPersister, int propIndex) {
        if (entityPersister.getPropertyCascadeStyles()[propIndex].doCascade(CascadingActions.DELETE)) {
            return true;
        }
        return false;
    }

    private static boolean isReferenceToParent(SessionFactoryImplementor sf, EntityPersister persister, int propertyIndex) {
        Type propType = persister.getPropertyTypes()[propertyIndex];
        if (!(propType instanceof AssociationType)) {
            return false;
        }
        String propertyColumn = getPropertyColumn(persister, propertyIndex);
        if (propertyColumn == null)
            return false;
        String otherName = ((AssociationType) propType).getAssociatedEntityName(sf);
        EntityPersister otherPersister = sf.getEntityPersister(otherName);
        Type[] otherTypes = otherPersister.getPropertyTypes();
        for (int i = 0; i < otherTypes.length; ++i) {
            if (isCascadeDelete(otherPersister, i)) {
                Type otherType = otherTypes[i];
                if (otherType instanceof AssociationType) {
                    AssociationType associationType = (AssociationType) otherType;
                    if (associationType instanceof CollectionType) {
                        CollectionType collectionType = (CollectionType) associationType;
                        if (!collectionType.getElementType(sf).isEntityType()) {
                            continue;
                        }
                    }
                    String assocName = associationType.getAssociatedEntityName(sf);
                    EntityPersister assocPersister = sf.getEntityPersister(assocName);
                    // FIXME: isSupertypeOf(assocName, entityName)
                    if (!assocPersister.getRootEntityName().equals(persister.getRootEntityName())) {
                        continue;
                    }
                    if (otherType instanceof CollectionType) {
                        CollectionType collectionType = (CollectionType) otherType;
                        String keyColumn = getCollectionKeyColumn(sf, collectionType.getRole());
                        if (propertyColumn.equals(keyColumn)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static Map toJSON(String dbtype, String entityName, Map object, TraversalStrategy ts) {
        if (object == null) {
            return null;
        }
        Map result = new HashMap();
        String type = getEntityName(dbtype, object, entityName);
        result.put("_type_", type);
        SessionImplementor si = (SessionImplementor) Context.getCurrent().getSession(dbtype, false);
        SessionFactoryImplementor sf = si.getFactory();
        ClassMetadata metadata = sf.getClassMetadata(type);
        EntityPersister ep = sf.getEntityPersister(type);
        EntityMetamodel emm = ep.getEntityMetamodel();
        EntityTuplizer et = ep.getEntityTuplizer();
        String idName = metadata.getIdentifierPropertyName();
        Serializable id = ep.getIdentifier(object, si);
        if (id != null) {
            result.put(idName, id);
        }
        Object version = et.getVersion(object);
        if (version != null) {
            String vName = emm.getVersionProperty().getName();
            result.put(vName, version);
        }
        String[] propNames = metadata.getPropertyNames();
        for (int i = 0; i < propNames.length; ++i) {
            String propName = propNames[i];
            if (!object.containsKey(propName)) {
                continue;
            }
            Type propType = metadata.getPropertyType(propName);
            if (propType instanceof AssociationType) {
                AssociationType associationType = (AssociationType) propType;
                if (!ts.canGo()) {
                    continue;
                }
                TraversalStrategy newTs = null;
                if (isCascadeDelete(ep, i)) {
                    if (ts.canGoDown()) {
                        newTs = ts.goDown();
                    }
                } else if (isReferenceToParent(sf, ep, i)) {
                    if (ts.canGoUp()) {
                        newTs = ts.goUp();
                    }
                } else if (ts.canGoToRef()) {
                    newTs = ts.goToRef();
                }
                if (newTs == null) {
                    continue;
                }
                Object value = object.get(propName);
                if (value == null) {
                    continue;
                }
                if (propType instanceof CollectionType && value instanceof Collection) {
                    if (associationType instanceof ListType && ((ListType) associationType).getElementType(sf).isEntityType()) {
                        String assocName = associationType.getAssociatedEntityName(sf);
                        result.put(propName, toJSON(dbtype, assocName, (Collection) value, newTs));
                    } else {
                        Hibernate.initialize(value);
                        result.put(propName, value);
                    }
                    continue;
                }
                if (propType instanceof EntityType && value instanceof Map) {
                    String assocName = associationType.getAssociatedEntityName(sf);
                    result.put(propName, toJSON(dbtype, assocName, (Map) value, newTs));
                    continue;
                }
            }
            Object value = object.get(propName);
            if (value == null) {
                continue;
            }
            if (propType instanceof CustomType) {
                result.put(propName, ((CustomType) propType).toString(value));
                continue;
            } else if (propType instanceof DateType) {
                result.put(propNames[i], jsonDateParser.format(value));
                continue;
            } else if (propType instanceof TimestampType) {
                result.put(propNames[i], jsonTimestampFormatter.format(value));
                continue;
            }
            Hibernate.initialize(value);
            result.put(propName, value);
        }
        return result;
    }

    public static String pp(Object obj) {
        try {
            return (new ObjectMapper()).writer().withDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String formatDate(Date value) {
        return jsonTimestampFormatter.format(value);
    }
    
    public static Date parseDate(String value) throws ParseException {
        return jsonTimestampFormatter.parse(value);
    }    
    
    public static String escape(String string) {
        if (string == null || string.length() == 0) {
            return "\"\"";
        }

        char c = 0;
        int i;
        int len = string.length();
        StringBuilder sb = new StringBuilder(len + 4);
        String t;

        //sb.append('"');
        for (i = 0; i < len; i += 1) {
            c = string.charAt(i);
            switch (c) {
                case '\\':
                    sb.append('\\');
                    sb.append(c);
                    break;
                case '/':
                    //                if (b == '<') {
                    sb.append('\\');
                    //                }
                    sb.append(c);
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                default:
                    if (c < ' ' || c > 'z' || c == '\"' || c == '\'') {
                        t = "000" + Integer.toHexString((int) c);
                        sb.append("\\u" + t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        //sb.append('"');
        return sb.toString();
    }

    public static String unescape(String str) {
        if (str == null) {
            return null;
        }
        try {
            StringWriter writer = new StringWriter(str.length());
            unescape(writer, str);
            return writer.toString();
        } catch (IOException ioe) {
            // this should never ever happen while writing to a StringWriter
            throw new UnhandledException(ioe);
        }
    }

    public static void unescape(Writer out, String str) throws IOException {
        if (out == null) {
            throw new IllegalArgumentException("The Writer must not be null");
        }
        if (str == null) {
            return;
        }
        int sz = str.length();
        StrBuilder unicode = new StrBuilder(4);
        boolean hadSlash = false;
        boolean inUnicode = false;
        for (int i = 0; i < sz; i++) {
            char ch = str.charAt(i);
            if (inUnicode) {
                // if in unicode, then we're reading unicode
                // values in somehow
                unicode.append(ch);
                if (unicode.length() == 4) {
                    // unicode now contains the four hex digits
                    // which represents our unicode character
                    try {
                        int value = Integer.parseInt(unicode.toString(), 16);
                        out.write((char) value);
                        unicode.setLength(0);
                        inUnicode = false;
                        hadSlash = false;
                    } catch (NumberFormatException nfe) {
                        throw new RuntimeException("Unable to parse unicode value: " + unicode, nfe);
                    }
                }
                continue;
            }
            if (hadSlash) {
                // handle an escaped value
                hadSlash = false;
                switch (ch) {
                    case '\\':
                        out.write('\\');
                        break;
                    case '\'':
                        out.write('\'');
                        break;
                    case '\"':
                        out.write('"');
                        break;
                    case 'r':
                        out.write('\r');
                        break;
                    case 'f':
                        out.write('\f');
                        break;
                    case 't':
                        out.write('\t');
                        break;
                    case 'n':
                        out.write('\n');
                        break;
                    case 'b':
                        out.write('\b');
                        break;
                    case 'u': {
                        // uh-oh, we're in unicode country....
                        inUnicode = true;
                        break;
                    }
                    default:
                        out.write(ch);
                        break;
                }
                continue;
            } else if (ch == '\\') {
                hadSlash = true;
                continue;
            }
            out.write(ch);
        }
        if (hadSlash) {
            // then we're in the weird case of a \ at the end of the
            // string, let's output it anyway.
            out.write('\\');
        }
    }
    /*
    public static String newId(){
    	UUID.randomUUID().toString()
    	return jsonTimestampFormatter.format(value);
    }*/

}
