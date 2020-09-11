package ru.neoflex.meta.utils.teneo;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.teneo.hibernate.mapping.SerializableDynamicEObjectImpl;

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by orlov on 22.07.2015.
 */
public class MSerializableDynamicEObjectImpl extends SerializableDynamicEObjectImpl {
    transient private Serializable id;
    transient private Object version;

    public MSerializableDynamicEObjectImpl(EClass eClass) {
        super(eClass);
    }

    public Object get(Object key) {
        EStructuralFeature sf = eClass().getEStructuralFeature((String) key);
        if (sf != null)
            return eGet(sf);
        if ("_type_".equals(key))
            return getTypeName();
        if ("e_id".equals(key))
            return id;
        if ("e_version".equals(key))
            return version;
        return null;
    }

    @Override
    public Object put(String key, Object value) {
        EStructuralFeature sf = eClass().getEStructuralFeature(key);
        if (sf != null) {
            Object result = eGet(sf);
            eSet(sf, value);
            return result;
        }
        if ("e_id".equals(key)) {
            Object result =  id;
            setId((Serializable) value);
            return result;
        }
        throw new RuntimeException(key + " not found");
    }

    public String getTypeName() {
        return eClass().getEPackage().getNsPrefix() + "." + eClass().getName();
    }

    public Set<Entry<String, Object>> entrySet() {
        Set<Entry<String, Object>> result = new LinkedHashSet<>();
        result.add(new AbstractMap.SimpleImmutableEntry<String, Object>("_type_", getTypeName()));
        if (id != null) {
            result.add(new AbstractMap.SimpleImmutableEntry<String, Object>("e_id", id));
        }
        if (version != null) {
            result.add(new AbstractMap.SimpleImmutableEntry<String, Object>("e_version", version));
        }
        for (Object key: keySet()) {
            Entry<String, Object> entry = new AbstractMap.SimpleImmutableEntry<String, Object>((String) key, get(key));
            result.add(entry);
        }
        return result;
    }

    public Serializable getId() {
        return id;
    }

    public void setId(Serializable id) {
        this.id = id;
    }

    public Object getVersion() {
        return version;
    }

    public void setVersion(Object version) {
        this.version = version;
    }
}
