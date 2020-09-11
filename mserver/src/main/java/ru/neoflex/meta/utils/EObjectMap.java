package ru.neoflex.meta.utils;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import ru.neoflex.meta.utils.teneo.MSerializableDynamicEObjectImpl;

import java.util.*;

public class EObjectMap implements Map<String, Object> {
    private EObject eObject;

    private EObjectMap(EObject eObject) {
        this.eObject = eObject;
    }

    public EObject getEObject() {
        return eObject;
    }

    public static Map wrap(Object eObject) {
        if (eObject == null) {
            return null;
        }
        if (eObject instanceof Map) {
            return (Map) eObject;
        }
        if (eObject instanceof EObject) {
            return new EObjectMap((EObject) eObject);
        }
        throw new RuntimeException("Wrap only Map or EObject");
    }

    public static Object unwrap(Map entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof EObjectMap) {
            EObjectMap eObjectMap = (EObjectMap) entity;
            return eObjectMap.eObject;
        }
        return entity;
    }

    @Override
    public int size() {
        return keySet().size();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        return keySet().contains(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return values().contains(value);
    }

    @Override
    public Object get(Object key) {
        EClass eClass = eObject.eClass();
        if ("_type_".equals(key))
            return eClass.getEPackage().getNsPrefix() + '.' + eClass.getName();
        if ("_eObject_".equals(key))
            return eObject;
        EStructuralFeature sf = eClass.getEStructuralFeature((String) key);
        if (sf != null)
            return eObject.eGet(sf);
        return null;
    }

    @Override
    public Object put(String key, Object value) {
        Object result = null;
        EClass eClass = eObject.eClass();
        EStructuralFeature sf = eClass.getEStructuralFeature(key);
        if (sf != null) {
            result = eObject.eGet(sf);
            eObject.eSet(sf, value);
        }
        return result;
    }

    @Override
    public Object remove(Object key) {
        return null;
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {
        for (Map.Entry<? extends String, ?> entry: m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {

    }

    @Override
    public Set<String> keySet() {
        LinkedHashSet set = new LinkedHashSet();
        set.add("_type_");
        set.add("_eObject_");
        if (eObject instanceof MSerializableDynamicEObjectImpl) {
            set.add("e_id");
        }
        for (EStructuralFeature sf: eObject.eClass().getEAllStructuralFeatures()) {
            set.add(sf.getName());
        }
        return set;
    }

    @Override
    public Collection<Object> values() {
        List result = new ArrayList();
        for (String key: keySet()) {
            Object value = get(key);
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        Set<Entry<String, Object>> result = new LinkedHashSet<>();
        for (String key: keySet()) {
            result.add(new AbstractMap.SimpleImmutableEntry<String, Object>(key, get(key)));
        }
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof EObjectMap) {
            EObjectMap eObjectMap = (EObjectMap) other;
            return eObject.equals(eObjectMap.eObject);
        }
        return eObject.equals(other);
    }

    @Override
    public int hashCode() {
        return eObject.hashCode();
    }

}
