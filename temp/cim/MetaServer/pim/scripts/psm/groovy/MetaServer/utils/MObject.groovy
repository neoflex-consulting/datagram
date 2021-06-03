package MetaServer.utils

import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.EReference
import org.eclipse.emf.ecore.util.EcoreUtil
import org.hibernate.internal.SessionImpl
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.Context
import ru.neoflex.meta.utils.teneo.MSerializableDynamicEObjectImpl

/**
 * Created by orlov on 21.02.2017.
 */
class MObject implements Map<String, Object> {
    EObject eObject

    @Override
    int size() {
        return keySet().size()
    }

    @Override
    boolean isEmpty() {
        return size() == 0
    }

    @Override
    boolean containsKey(Object key) {
        return keySet().contains(key)
    }

    @Override
    boolean containsValue(Object value) {
        return values().contains(value)
    }

    @Override
    Object get(Object key) {
        if ("eObject".equals(key))
            return eObject;
        def eClass = eObject.eClass()
        if ("_type_".equals(key))
            return eClass.getEPackage().getNsPrefix() + '.' + eClass.name;
        def sf = eClass.getEStructuralFeature((String) key);
        if (sf != null)
            return eObject.eGet(sf);
        if (eObject instanceof MSerializableDynamicEObjectImpl) {
            def m = (MSerializableDynamicEObjectImpl) eObject
            if ("e_id".equals(key))
                return m.getId();
            if ("e_version".equals(key))
                return m.getVersion();
        }
        return null;
    }

    @Override
    Object put(String key, Object value) {
        def result = null
        def sf = getMClass().getFeature((String) key);
        if (sf != null) {
            result = eObject.eGet(sf);
            eObject.eSet(sf, value);
        }
        return result
    }

    @Override
    Object remove(Object key) {
        return null
    }

    @Override
    void putAll(Map<? extends String, ?> m) {
        m.entrySet().each {put(it.key, it.value)}
    }

    @Override
    void clear() {

    }

    @Override
    Set<String> keySet() {
        def set = new LinkedHashSet(["_type_"])
        if (eObject instanceof MSerializableDynamicEObjectImpl) {
            set += ["e_id", "e_version"]
        }
        return set + getMClass().keySet()
    }

    @Override
    Collection<Object> values() {
        return entrySet().collect {it.value}
    }

    @Override
    Set<Map.Entry<String, Object>> entrySet() {
        return new LinkedHashSet<>(keySet().collect {new AbstractMap.SimpleImmutableEntry<String, Object>((String) it, get(it))})
    }

    private MObject(EObject base) {
        this.eObject = base
    }

    MObject merge(MObject base) {
        mergeImpl(base, [:])
    }

    def delete() {
        Database.new.delete(getTypeName(), this)
    }

    MObject save() {
        return saveImpl([:])
    }

    MObject saveImpl(Map merged) {
        if (get("e_id") == null && !merged.containsKey(eObject)) {
            saveContainment(merged)
            decodeInternalReferences(merged)
        }
        return this
    }

    MObject saveContainment(Map merged) {
        def keyObject = eObject
        def valueObject = this.toMap().eObject
        merged.put(keyObject, valueObject)
        keySet().findAll {isReference(it) && isContainment(it)}.each {
            def base = get(it)
            if (base != null) {
                if (isMany(it)) {
                    (base as List).each {wrap(it).saveContainment(merged)}
                }
                else {
                    wrap(base).saveContainment(merged)
                }
            }
        }
        Database.new.save(eObject)
        return this
    }

    private MObject decodeInternalReferences(Map merged) {
        // 2 pass - noncontainment references
        for (it in getMClass().keySet()) {
            if (isReference(it)) {
                def value = get(it)
                if (value == null) {
                    continue
                }
                if (isContainment(it)) {
                    if (isMany(it)) {
                        (value as List).each {wrap(it).decodeInternalReferences(merged)}
                    }
                    else {
                        wrap(value).decodeInternalReferences(merged)
                    }
                }
                else {
                    if (isMany(it)) {
                        def newList = (value as List).collect { decodeValue(it, merged)}
                        (value as List).clear()
                        (value as List).addAll(newList)
                    }
                    else {
                        if (!isContainer(it)) {
                            put(it, decodeValue(value, merged))
                        }
                    }
                }
            }
        }
        return this
    }
    static private Map decodeValue(EObject value, Map merged) {
        if (wrap(value).e_id != null) {
            return value
        }
        def decoded = merged.get(value)
        if (decoded == null) {
            throw new RuntimeException("decoded value not found: ${value}")
        }
        return decoded
    }
    private MObject mergeImpl(MObject base, Map merged) {
        mergeContained(base, merged)
        decodeInternalReferences(merged)
        return this
    }
    private MObject mergeContained(MObject base, Map merged) {
        if (merged.containsKey(base.eObject)) {
            eObject = merged.get(base.eObject)
            return this
        }
        merged.put(base.eObject, eObject)
        for (it in getMClass().keySet()) {
            def fromValue = get(it)
            def toValue = base.get(it)
            if (toValue == null) {
                if (fromValue != null) {
                    put(it, null)
                }
            }
            else if (isAttribute(it) && !isProtectedAgainstMerge()) {
                if (isMany(it)) {
                    if (!listsEquals(fromValue as List, toValue as List)) {
                        (fromValue as List).clear()
                        (fromValue as List).addAll(toValue)
                    }
                }
                else {
                    if (fromValue != toValue) {
                        put(it, toValue)
                    }
                }
            }
            else if (isReference(it)) {
                if (isContainment(it)) {
                    if (isMany(it)) {
                        def fromList = fromValue as List
                        def toList = toValue as List
                        def mergedList = []
                        def hasName = MetaServer.utils.MClass.wrap((getMClass().getFeature(it) as EReference).EReferenceType).keySet().contains("name")
                        def i = 0
                        for (toItem in toList) {
                            def toWrapped = wrap(toItem)
                            if (!hasName) {
                                if (i >= fromList.size()) {
                                    mergedList.add(toWrapped.saveImpl(merged).eObject)
                                }
                                else {
                                    def fromWrapped = wrap(fromList.get(i))
                                    if (fromWrapped._type_ == toWrapped._type_) {
                                        mergedList.add(fromWrapped.mergeContained(toWrapped, merged).eObject)
                                    }
                                    else {
                                        mergedList.add(toWrapped.saveImpl(merged).eObject)
                                    }
                                }
                            }
                            else {
                                def fromItem = fromList.find {wrap(it).name == toWrapped.name}
                                if (fromItem == null) {
                                    mergedList.add(toWrapped.saveImpl(merged).eObject)
                                }
                                else {
                                    mergedList.add(wrap(fromItem).mergeContained(toWrapped, merged).eObject)
                                }
                            }
                            i += 1
                        }
                        fromList.clear()
                        fromList.addAll(mergedList)
                    }
                    else {
                        if (it == "auditInfo") {
                            put(it, null)
                            continue
                        }
                        def fromWrapped = wrap(fromValue)
                        def toWrapped = wrap(toValue)
                        if (fromWrapped.eObject != null && fromWrapped._type_ == toWrapped._type_) {
                            fromWrapped.mergeContained(toWrapped, merged)
                        }
                        else {
                            put(it, toWrapped.saveImpl(merged).eObject)
                        }
                    }
                }
                else { // need to decode references in 2 pass
                    if (isMany(it)) {
                        (fromValue as List).clear()
                        (fromValue as List).addAll(toValue)
                    }
                    else {
                        if (!isContainer(it)) {
                            put(it, toValue)
                        }
                    }
                }
            }
        }
        return this
    }

    static boolean listsEquals(List list1, List list2) {
        if (list1.size() != list2.size()) {
            return false
        }
        list1.eachWithIndex { Object entry, int i ->
            if (entry != list2.get(i))
                return false
        }
        return true;
    }

    String getTypeName() {
        return getMClass().getTypeName()
    }

    static MObject wrap(EObject base) {
        return new MObject(base)
    }

    MObject clone() {
        return wrap(copy(this.eObject))
    }

    MObject toMap() {
        if (!(eObject instanceof Map)) {
            this.eObject = copy(this.eObject)
        }
        return this
    }

    static EObject copy(EObject base) {
        def copier = new EcoreUtil.Copier() {
            protected EObject createCopy(EObject eObject) {
                def entityName = wrap(eObject)._type_
                return (Context.current.session as SessionImpl).instantiate(entityName, null)

            }
        }
        def eObject = copier.copy(base)
        return eObject
    }

    boolean isMany(String name) {
        return getMClass().isMany(name)
    }

    boolean isAttribute(String name) {
        return getMClass().isAttribute(name)
    }

    boolean isProtectedAgainstMerge() {
        def protectAttribute = getMClass().getAnnotation("mspace.ui", "protectAttribute", null)
        if (protectAttribute != null) {
            return get(protectAttribute) == true
        }
        return false
    }

    boolean isReference(String name) {
        return getMClass().isReference(name)
    }

    boolean isContainment(String name) {
        return getMClass().isContainment(name)
    }

    boolean isContainer(String name) {
        return getMClass().isContainer(name)
    }

    boolean same(MObject other) {
        return get('_type_') == other._type_ && get('e_id') == other.e_id
    }

    MObject getContainer() {
        for (key in keySet().findAll {isContainer(it)}) {
            def eObject = get(key)
            if (eObject != null) {
                return wrap(eObject)
            }
        }
        return null
    }

    MObject getRootContainer() {
        def current = this
        while (true) {
            def container = current.getContainer()
            if (container == null) {
                break;
            }
            current = container
        }
        return current
    }

    MetaServer.utils.MClass getMClass() {
        return MetaServer.utils.MClass.wrap(eObject.eClass())
    }
}
