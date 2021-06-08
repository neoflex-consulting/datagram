package MetaServer.utils

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.EReference
import org.eclipse.emf.ecore.EStructuralFeature
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.ECoreUtils
import ru.neoflex.meta.utils.teneo.MSerializableDynamicEObjectImpl

class ECoreHelper {
    private final static Log logger = LogFactory.getLog(ECoreHelper.class);

    def referenceObjects = [:]
    def allClasses = null
    def allReferences = null
    def toObject = [:]
    def fromObject = [:]
    def referencesFrom = [:]
    def referencesTo = [:]
    def objects = [:]
    def isolatedClasses = null

    static String getAnnotation(EClass eClass, String source, String key, String dflt) {
        def eAnnotation = eClass.EAnnotations.find {it.source == source}
        if (eAnnotation != null) {
            def detail = eAnnotation.details.find {it.key == key}
            if (detail != null) {
                return detail.value
            }
        }
        return dflt
    }
    def boolean isClassIsolated(EClass eClass, Set isolated) {
        def qName = getQName(eClass)
        if (isolated.contains(qName)) return true
        if (getAnnotation(eClass, "mspace.ui", "isolated", "false") == "true") {
            isolated.add(qName)
            return true
        }
        def result = getReferencesFrom(eClass).every {
            it.containment && isClassIsolated(it.EReferenceType, isolated) ||
                    !it.containment && it.EOpposite != null && it.EOpposite.containment
        }
        if (result) isolated.add(qName)
        if (result && !getChildren(eClass).every {isClassIsolated(it, isolated)}) {
            result = false
            isolated.remove(qName)
        }
        return result
    }
    def Set getIsolatedClasses() {
        if (isolatedClasses == null) {
            isolatedClasses = getAllClasses().findAll {isClassIsolated(it, [].toSet())}.collect {getQName(it)}.toSet()
        }
        return isolatedClasses
    }
    List<EClass> getAllClasses() {
        if (allClasses == null) {
            allClasses = ECoreUtils.allPackages().collectMany {it.getEClassifiers().findAll {it instanceof EClass}}
        }
        return allClasses
    }

    List<EClass> getChildren(EClass eClass) {
        return getAllClasses().findAll {it.ESuperTypes.contains(eClass)}
    }

    List<EReference> getAllReferences() {
        if (allReferences == null) {
            allReferences = getAllClasses().collectMany {it.EReferences}
        }
        return allReferences
    }
    List<EReference> getReferencesTo(EClass eClass) {
        def qName = getQName(eClass)
        def result = referencesTo.get(qName)
        if (result == null) {
            result = getAllReferences().findAll {it.EReferenceType.isSuperTypeOf(eClass)}
            referencesTo[qName] = result
        }
        return result
    }
    List<EReference> getReferencesFrom(EClass eClass) {
        def qName = getQName(eClass)
        def result = referencesFrom.get(qName)
        if (result == null) {
            result = getAllReferences().findAll {it.EContainingClass.isSuperTypeOf(eClass)}
            referencesFrom[qName] = result
        }
        return result
    }
    Map getRootContainer(Map mObject) {
        if (mObject == null) {
            return null
        }
        if (mObject.rootContainer == null) {
            def tempObject = mObject
            while (true) {
                def parent = getContainer(tempObject)
                if (parent == null) {
                    mObject.rootContainer = tempObject
                    break
                }
                tempObject = parent
            }
        }
        return mObject.rootContainer
    }
    boolean hasCommonRoot(Map mObject1, Map mObject2) {
        if (mObject1 == null || mObject2 == null) {
            return false
        }
        return getRootContainer(mObject1).hash == getRootContainer(mObject2).hash
    }
    Map getContainer(Map mObject) {
        def containers = getReferencesTo(getObjectClass(mObject)).findAll {it.isContainment()}.collectMany {getFromObjects(it, mObject).collect {it[0]}}
        if (containers.size() == 0) return null
        return containers.get(0)
    }
    List<Map> getContainedObjects(Map mObject) {
        return getReferencesFrom(getObjectClass(mObject)).findAll {it.isContainment()}.collectMany {getToObjects(mObject, it).collect {it[1]}}
    }
    List<Map> getObjectGraph(Map mObject) {
        return [mObject] + getContainedObjects(mObject).collectMany {getObjectGraph(it)}
    }
    List<Map> getGraphDependentObjects(Map mObject) {
        return getObjectGraph(mObject).collectMany { graphObject->
            getReferencesTo(getObjectClass(graphObject)).findAll {!it.isContainment()}.collectMany {getFromObjects(it, graphObject)}.collect {getRootContainer(it[0])}
        }.unique {it.hash}
    }
    List<Map> getGraphDependentRootObjects(Map mObject) {
        return getGraphDependentObjects(mObject). collect {getRootContainer(it)}.unique {it.hash}
    }
    List<List> getReferencedObjects(Map mObject) {
        return getReferencesFrom(getObjectClass(mObject)).findAll {!it.isContainment()}.collectMany {getToObjectsOrNull(mObject, it)}
    }
    List<Map> getGraphReferencedRootObjects(Map mObject) {
        return getGraphLinksWithRoot(mObject, mObject).findAll {it[1] != null}. collect {getRootContainer(it[1])}.unique {it.hash}
    }
    List<List> getGraphLinks(Map mObject) {
        return getGraphLinksWithRoot(mObject, mObject).collect {
            getRootContainer(it[0])
            getRootContainer(it[1])
            it
        }
    }
    List<List> getGraphLinksWithRoot(Map mObject, Map rootObject) {
        if (getIsolatedClasses().contains(mObject._type_)) {
            return []
        }
        return (getReferencedObjects(mObject).findAll {!hasCommonRoot(it[1], rootObject)} +
                getContainedObjects(mObject).collectMany {getGraphLinksWithRoot(it, rootObject)})
    }
    List<Map> getAllReferencedObjects(Map mObject) {
        def result = [mObject]
        def size = result.size()
        while (true) {
            result = [mObject] + result.collectMany {getGraphReferencedRootObjects(it)}.unique {it.hash}
            if (result.size() <= size) break
            size = result.size()
        }
        return result
    }
    List<Map> getAllDependentObjects(Map mObject) {
        def result = [mObject]
        def seen = 0
        while (true) {
            def newDependentObjects = result[seen..result.size() - 1]
                    .collectMany {getGraphDependentRootObjects(it)}
                    .findAll {m -> !result.any {m.hash == it.hash}}
                    .unique {it.hash}
            if (newDependentObjects.size() == 0) break
            seen = result.size()
            result.addAll(newDependentObjects)
        }
        return result
    }
    boolean isTopLevel(EClass eClass) {
        return eClass.EAllAttributes.any {it.name == 'name'} && !getReferencesFrom(eClass).any {it.EOpposite != null && it.EOpposite.isContainment()}
    }
    List<Map> getAllReferencedObjectsOfDependedObjects(EObject eObject) {
        def mObject = getObject(getQName(eObject.eClass()), getEId(eObject), eObject.name)
        return getGraphDependentObjects(mObject)
                .collectMany {getAllReferencedObjects(it)}
                .unique {it.hash}
                .findAll {isTopLevel(getObjectClass(it))}
    }
    List<Map> getAllDependentObjectsOfEntity(Map entity) {
        def mObject = getObject(entity._type_, entity.e_id, entity.name)
        return getAllDependentObjects(mObject)
    }
    static Serializable getEId(EObject eObject) {
        return (eObject as MSerializableDynamicEObjectImpl).getId()
    }
    static String getQName(EClass eClass) {
        return "${eClass.EPackage.nsPrefix}.${eClass.name}".toString()
    }
    static String getReferenceHash(EReference eReference) {
        return "${getQName(eReference.EContainingClass)}.${eReference.name}".toString()
    }
    EClass getObjectClass(Map mObject) {
        if (mObject.eClass == null) {
            mObject.eClass = getAllClasses().find {getQName(it) == mObject._type_}
        }
        return mObject.eClass
    }
    Map getObject(String typeName, Serializable e_id, String name = null) {
        def hash = "${typeName}#${e_id}".toString()
        def result = objects.get(hash)
        if (result == null) {
            result = [_type_: typeName, e_id: e_id, name: name, hash: hash]
            objects[hash] = result
        }
        return result
    }
    boolean hasAttribute(EClass eClass, String attr) {
        return eClass.getEAllAttributes().any {it.name == attr}
    }
    boolean isCollection(EStructuralFeature eStructuralFeature) {
        return eStructuralFeature.upperBound == -1 || eStructuralFeature.upperBound > 1
    }
    List<List<Map>> getReferenceObjects(EReference eReference) {
        def hash = getReferenceHash(eReference)
        def result = referenceObjects.get(hash)
        if (result == null) {
            def indexExpr = isCollection(eReference) ? "index(t)" : "-1"
            def nameFromExpr = hasAttribute(eReference.EContainingClass, "name") ? "f.name" : "''"
            def nameToExpr = hasAttribute(eReference.EReferenceType, "name") ? "t.name" : "''"
            result = Database.new.select("select type(f), f.e_id, ${nameFromExpr}, type(t), t.e_id, ${nameToExpr}, ${indexExpr} from ${getQName(eReference.EContainingClass)} f join f.${eReference.name} t", [:])
                .collect {
                    def from = getObject(it[0], it[1], it[2])
                    def to = getObject(it[3], it[4], it[5])
                    if (eReference.containment) {
                        to.container = from
                        to.containerReference = eReference
                        to.index = it[6]
                    }
                    [from, to, eReference]
                }
            referenceObjects[hash] = result
        }
        return result
    }
    List<List> getFromObjects(EReference eReference, Map mObject) {
        def hash = mObject.hash
        def rhash = getReferenceHash(eReference) + "." + hash
        def result = fromObject.get(rhash)
        if (result == null) {
            result = getReferenceObjects(eReference).findAll {it[1].hash == hash}
            fromObject[rhash] = result
        }
        return result
    }
    List<List> getToObjects(Map mObject, EReference eReference) {
        def hash = mObject.hash
        def rhash = hash + "." + getReferenceHash(eReference)
        def result = toObject.get(rhash)
        if (result == null) {
            result = getReferenceObjects(eReference).findAll {it[0].hash == hash}
            toObject[rhash] = result
        }
        return result
    }
    List<List> getToObjectsOrNull(Map mObject, EReference eReference) {
        def result = getToObjects(mObject, eReference)
        return result.size() == 0 ? [[mObject, null, eReference]] : result
    }
}
