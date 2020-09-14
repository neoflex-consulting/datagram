package MetaServer.utils

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.eclipse.emf.ecore.EAttribute
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EEnum
import org.eclipse.emf.ecore.EReference
import org.eclipse.emf.ecore.EStructuralFeature
import org.hibernate.internal.SessionImpl
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.Context

import java.text.SimpleDateFormat

/**
 * Created by orlov on 22.02.2017.
 */
class MClass {
    private final static Log logger = LogFactory.getLog(MClass.class);
    private final static SimpleDateFormat jsonDateParser = new SimpleDateFormat("yyyy-MM-dd");
    private final static SimpleDateFormat jsonTimestampParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    EClass eClass

    MClass() {
    }
    MClass(EClass eClass) {
        this.eClass = eClass
    }

    static MClass wrap(EClass base) {
        return new MClass(base)
    }
    Set<String> keySet() {
        return new LinkedHashSet<String>(eClass.getEAllStructuralFeatures().collect {it.getName()})
    }

    EStructuralFeature getFeature(String name) {
        return eClass.getEStructuralFeature(name);
    }

    boolean isMany(String name) {
        def sf = getFeature(name);
        return sf!= null && sf.isMany()
    }

    boolean isAttribute(String name) {
        return getFeature(name) instanceof EAttribute
    }

    boolean isReference(String name) {
        return getFeature(name) instanceof  EReference
    }

    boolean isContainment(String name) {
        def sf = getFeature(name);
        return sf instanceof EReference && (sf as EReference).isContainment()
    }

    boolean isContainer(String name) {
        def sf = getFeature(name);
        return sf instanceof EReference && (sf as EReference).isContainer()
    }

    MClass getContainer () {
        for (key in keySet().findAll {isContainer(it)}) {
            EReference reference = getFeature(key) as EReference
            return wrap(reference.EReferenceType)
        }
        return null
    }

    String getTypeName() {
        return eClass.getEPackage().getNsPrefix() + '.' + eClass.name
    }

    MObject instantiate() {
        return MObject.wrap((Context.current.session as SessionImpl).instantiate(getTypeName(), null))
    }

    static MClass get(String typeName) {
        def nsPrefix = typeName.split("[.]").first()
        return MPackage.get(nsPrefix).classes().find {it.typeName == typeName}
    }

    static List<MClass> all() {
        return MPackage.all().collectMany {it.classes()}
    }

    List<EReference> getReferences() {
        return all().collectMany {it.eClass.EAllReferences.findAll {it.EReferenceType.isSuperTypeOf(eClass)}}
    }

    static List<MClass> getTopLevels() {
        return all().findAll {it.isTopLevel()}
    }

    List<EClass> getChildren() {
        return all().findAll {it.eClass.EAllSuperTypes.contains(eClass)}
    }
    boolean isTopLevel() {
        return children.isEmpty() && keySet().contains("name") &&  references.findAll {it.isContainment()}.isEmpty()
    }

    private static deleteDuplicates(typeName) {
        for (l in Database.new.select("select t.name,count(*) from ${typeName} t group by t.name having count(*) > 1", null)) {
            def name = ((List)l).get(0)
            def count = ((List)l).get(1)
            for (t in Database.new.select("from ${typeName} where name=:name", [name: name])) {
                if (count > 1) {
                    try {
                        Database.new.delete(t._type_, t)
                        Context.current.savepoint()
                        count -= 1
                    }
                    catch (Exception e) {
                        logger.error(e.message)
                        Context.current.rollbackResources()
                    }
                }
            }
        }
    }

    public static deduplicateTopLevels() {
        topLevels.each {deleteDuplicates(it.typeName)}
        deleteDuplicates('rel.Scheme')
    }

    String getAnnotation(String source, String key, String dflt) {
        return ECoreHelper.getAnnotation(eClass, source, key, dflt)
    }

    static Object decodeAttributeValue(MClass mClass, String name, Object value) {
        if ((mClass.getFeature(name) as EAttribute).EAttributeType instanceof EEnum) {
            return value.toString()
        }
        if (value instanceof java.sql.Timestamp) {
            return jsonTimestampParser.format(value)
        }
        if (value instanceof java.util.Date) {
            return jsonDateParser.format(value)
        }
        return value
    }
    static readContained(Map entity, Map params) {
        params.e_id = Integer.parseInt(params.e_id)
        return readObject(params)
    }
    static Map readObject(Map object) {
        def mClass = MClass.get(object._type_)
        def keySet = mClass.keySet()
        def toOneList = keySet.findAll {!mClass.isMany(it)}
        if (toOneList.size() > 0) {
            def selectList = toOneList.collect {
                if (mClass.isAttribute(it)){
                    "e.${it}"
                } else {
                    def refClass = MClass.wrap((mClass.getFeature(it) as EReference).EReferenceType).getTypeName()
                    ["case ${it}_ when null then '${refClass}' else type(${it}_) end", "${it}_.e_id"]
                }
            }.flatten().join(", ")
            def joinList = keySet.findAll {!mClass.isMany(it)}.findAll {mClass.isReference(it)}.collect {
                "left join e.${it} as ${it}_"
            }.flatten().join(" ")
            def query = "select ${selectList} from ${object._type_} e ${joinList} where e.e_id=${object.e_id}".toString()
            def row = Context.current.session.createQuery(query).uniqueResult() as List
            if (row == null) {
                return object
            }
            def col = 0
            keySet.findAll {!mClass.isMany(it)}.each {
                if (mClass.isAttribute(it)) {
                    object[it] = MClass.decodeAttributeValue(mClass, it, row[col])
                    col += 1
                }
                else {
                    if (row[col + 1] != null) {
                        object[it] = [_type_: row[col], e_id: row[col + 1]]
                    }
                    col += 2
                }
            }
        }
        keySet.findAll {mClass.isMany(it)}.findAll {mClass.isAttribute(it)}.each { attr ->
            def attrQuery = "select elements(e.${attr}) from ${object._type_} e where e.e_id=${object.e_id}".toString()
            object[attr] = Context.current.session.createQuery(attrQuery).list().collect {
                MClass.decodeAttributeValue(mClass, attr, it)
            }
        }
        keySet.findAll {mClass.isReference(it)}.findAll {mClass.isMany(it)}.each {
            def refQuery = "select type(${it}_), ${it}_.e_id from ${object._type_} e join e.${it} as ${it}_ where e.e_id=${object.e_id}".toString()
            object[it] = Context.current.session.createQuery(refQuery).list().collect {
                [_type_: it[0], e_id: it[1]]
            }
        }
        keySet.findAll {mClass.isReference(it)}.findAll {mClass.isContainment(it)}.findAll {!mClass.isMany(it)}.each {
            if (object[it] != null) {
                object[it] = readObject(object[it])
            }
        }
        keySet.findAll {mClass.isReference(it)}.findAll {mClass.isContainment(it)}.findAll {mClass.isMany(it)}.each {
            object[it] = object[it].collect {
                readObject(it)
            }
        }
        return object
    }

}
