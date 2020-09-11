package MetaServer.utils

import groovy.json.JsonOutput
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EPackage
import org.hibernate.Query
import org.hibernate.Session
import ru.neoflex.meta.utils.Context

/**
 * Created by orlov on 25.06.2016.
 */
class MetaInfo {
    private final static Log logger = LogFactory.getLog(MetaInfo.class)
    private static Map metaInfo = null

    static List getAllDeps(Map entity) {
        return getAllDeps(getClassInfos(), entity, [])
    }

    static List getAllDirectDeps(Map entity) {
        return getAllDirectDeps(getClassInfos(), entity, [])
    }

    static List getAllRefsOfAllDirectDepsWithRefs(Map entity) {
        def classes = getClassInfos()
        def found = []
        for (dep in getAllDirectDeps(classes, entity, found).collect {it}) {
            getAllRefs(classes, dep, found)
        }
        found = found.findAll {it.e_id != null}.unique {"${it._type_}|${it.e_id}"}
        for (ent in found) {
            ent.refs = []
            getAllExternalRefs(classes, ent, ent.refs)
        }
        return found
    }

    static List getParents(Map dep, Map entity, List seen) {
        def sql = "select ent from ${dep.className} ent join ent.${dep.name} ref where ref.e_id=:e_id"
        return listEntities(sql.toString(), dep, entity, seen)
    }

    static List getChildren(Map dep, Map entity, List seen) {
        if (entity.e_id != null) {
            def sql = "select ref from ${dep.className} ent join ent.${dep.name} ref where ent.e_id=:e_id"
            def s = seen.size()
            listEntities(sql.toString(), dep, entity, seen)
            if (s == seen.size()) {
                seen.add([name: null, _type_: dep.refClassName, e_id: null, dep: dep, path: entity])
            }
        }
        return seen
    }

    private static List listEntities(String sql, Map dep, Map entity, List seen) {
        Session session = Context.getCurrent().getSession("teneo", false)
        Query query = session.createQuery(sql)
        query.setParameter("e_id", (Long)entity.e_id)
        for (depEntity in query.list()) {
            def name = depEntity.get("name")
            def index = null
            if (name == null && dep.isCollection) {
                def e = session.get(entity._type_, (Long)entity.e_id)
                try {
                    index = e.get(dep.name)?.findIndexOf {it.e_id == depEntity.e_id}
                }
                catch (Throwable throwable) {
                    logger.error("[${entity._type_}/${entity.e_id}].${dep.name}: ${throwable.getMessage()}".toString())
                }
            }
            seen.add([name: name, _type_: depEntity._type_, e_id: depEntity.e_id, dep: dep, path: entity, index: index])
        }
        return seen
    }

    static List getDirectDeps(Map classes, String entityType, Map entity, List seen) {
        for (dep in getAllClassDeps(classes, entityType, []).findAll{!it.isContainment && !it.isOppositeContainment}) {
            getParents(dep, entity, seen)
        }
        return seen
    }

    static List getDirectRefs(Map classes, String entityType, Map entity, List seen) {
        for (ref in getAllClassRefs(classes, entityType, [:]).values().findAll {!it.isContainment && !it.isOppositeContainment}) {
            getChildren(ref, entity, seen)
        }
        return seen
    }

    static List getAllDirectDeps(Map classes, Map entity, List seen) {
        return getAllDirectDeps(classes, entity._type_, entity, seen)
    }

    static List getAllDirectDeps(Map classes, String entityType, Map entity, List seen) {
        for (childRef in getAllClassRefs(classes, entityType, [:]).values().findAll {it.isContainment}) {
            for (child in getChildren(childRef, entity, []).findAll {it._type_ != null}) {
                getAllDirectDeps(classes, child._type_, child, seen)
            }
        }
        return getDirectDeps(classes, entityType, entity, seen)
    }

    static List getAllDirectRefs(Map classes, Map entity, List seen) {
        return getAllDirectRefs(classes, entity._type_, entity, seen)
    }

    static List getAllDirectRefs(Map classes, String entityType, Map entity, List seen) {
        for (childRef in getAllClassRefs(classes, entityType, [:]).values().findAll {it.isContainment}) {
            for (child in getChildren(childRef, entity, []).findAll {it._type_ != null}) {
                getAllDirectRefs(classes, child._type_, child, seen)
            }
        }
        return getDirectRefs(classes, entityType, entity, seen)
    }

    static Map getContainer(Map classes, Map entity) {
        entity.container = null
        if (entity.e_id != null) {
            for (containerDep in getAllClassDeps(classes, entity._type_, []).findAll {it.isContainment}) {
                def containers = getParents(containerDep, entity, [])
                if (containers.size() > 0) {
                    def container = containers.get(0)
                    entity.container = container
                    entity.containerDep = containerDep
                    return getContainer(classes, container)
                }
            }
        }
        return entity
    }

    static List getAllDeps(Map classes, Map entity, List found) {
        def toSee = [entity]
        def seen = []
        def level = 0
        while (toSee.size() > 0) {
            def toSeeEntity = toSee.remove(0)
            seen.add(toSeeEntity)
            for (ref in getAllDirectDeps(classes, toSeeEntity, [])) {
                def container = getContainer(classes, ref)
                container.level = level
                if (!found.any {it._type_ == container._type_ && it.e_id == container.e_id}
                    && !(entity._type_ == container._type_ && entity.e_id == container.e_id)) {
                    found.add(container)
                }
                if (!seen.any {it._type_ == ref._type_ && it.e_id == ref.e_id}) {
                    toSee.add(ref)
                }
            }
            level += 1
        }
        return found.sort {-it.level}
    }

    static List getAllRefs(Map classes, Map entity, found) {
        found.removeAll {it._type_ == entity._type_ && it.e_id == entity.e_id}
        found.add(entity)
        if (classes[entity._type_].hasExternalRefs) {
            def toSee = [entity]
            def seen = []
            while (toSee.size() > 0) {
                def toSeeEntity = toSee.remove(0)
                seen.add(toSeeEntity)
                for (ref in getAllDirectRefs(classes, toSeeEntity, [])) {
                    if (ref.e_id != null) {
                        def container = getContainer(classes, ref)
                        found.add(container)
                    }
                    else {
                        if (!classContains(classes, entity._type_, ref._type_)) {
                            found.add(ref)
                        }
                    }
                    if (ref.e_id != null && !seen.any {it._type_ == ref._type_ && it.e_id == ref.e_id} &&
                            !toSee.any {it._type_ == ref._type_ && it.e_id == ref.e_id} &&
                            classes[ref._type_].hasExternalRefs) {
                        toSee.add(ref)
                    }
                }

            }
        }
        return found
    }

    static getAllExternalRefs(Map classes, Map entity, List found) {
        if (classes[entity._type_].hasExternalRefs) {
            def toSee = [entity]
            def seen = []
            while (toSee.size() > 0) {
                def toSeeEntity = toSee.remove(0)
                seen.add(toSeeEntity)
                for (ref in getAllDirectRefs(classes, toSeeEntity, [])) {
                    if (ref.e_id != null) {
                        def container = getContainer(classes, ref)
                        if (container._type_ != entity._type_ || container.e_id != entity.e_id) {
                            found.add(ref)
                        }
                    }
                    else {
                        if (!classContains(classes, entity._type_, ref._type_)) {
                            found.add(ref)
                        }
                        else if (ref.e_id != null &&
                                !seen.any {it._type_ == ref._type_ && it.e_id == ref.e_id} &&
                                !toSee.any {it._type_ == ref._type_ && it.e_id == ref.e_id} &&
                                classes[ref._type_].hasExternalRefs) {
                            toSee.add(ref)
                        }
                    }
                }

            }
        }
        return found
    }

    static Map getClassInfos() {
        return getMetaInfo().classInfos
    }

    static boolean isTopLevelClass(classes, it) {
        if ((classes[it.name].anns as Map).get("mspace.ui", [:]).toplevel == "true") return true
        return getAllClassAtts(getClassInfos(), it.name, [:]).containsKey("name") &&
                !classHasRefToContainer(classes, it.name) &&
                !classHasDepFromContainer(classes, it.name)
    }

    static boolean isTopLevelEntity(entity) {
        def classes = getClassInfos()
        return isTopLevelClass(classes, classes[entity._type_])
    }

    static List getTopLevelClasses(classes) {
        return classes.values().findAll {isTopLevelClass(classes, it)}
    }

    static boolean classHasRefToContainer(Map classes, String className) {
        return getAllClassRefs(classes, className, [:]).values().any {it.isOppositeContainment}
    }

    static boolean classHasDepFromContainer(Map classes, String className) {
        return getAllClassDeps(classes, className, []).any {it.isContainment}
    }
    private static List queryList(session, sql) {
        try {
            return session.createQuery(sql).list()
        }
        catch (Throwable e) {
            logger.error(sql, e)
            return []
        }
    }

    static List getTopLevelEntities(boolean lost) {
        def classes = getClassInfos()
        def entities = new HashSet()
        def topClasses = getTopLevelClasses(classes)
        Session session = Context.current.getSession("teneo", false)
        for (entityClass in topClasses) {
            for (entity in queryList(session, "from ${entityClass.name}").findAll{it?.name}.collect {[name: it?.name, _type_: it._type_, e_id: it.e_id]}) {
                def container = getContainer(classes, entity)
                def classLost = classHasRefToContainer(classes, container._type_)
                if (lost && classLost || !lost &&!classLost) {
                    entities.add([_type_: container._type_, name: container.name, e_id: container.e_id])
                }
            }
        }
        return entities.asList()
    }

    static Map getRootClass(Map classes, Map cls) {
        if (cls.supers.size() == 0) {
            return cls
        }
        return getRootClass(classes, classes[cls.supers.get(0)])
    }

    static String getTableName(Map classes, String className) {
        def rootClassName = getRootClass(classes, classes[className]).name
        return rootClassName.tokenize('.').collect{it.toUpperCase()}.join('_')
    }

    static List getLost(List deleted, List restored) {
        def classes = getClassInfos()
        def toDelete = []
        def skipped = []
        logger.info("Delete lost objects with parent reference or restore reference")
        for (entityClass in classes.values()) {
            for (ref in entityClass.refs.values().findAll {it.isOppositeContainment}) {
                def oppositeName = ref.oppositeName
                def opposite = classes[ref.refClassName].refs[oppositeName]
                def eName = (classes[ref.className].atts as Map).containsKey("name") ? "ent.name" : "'<undefined>'"
                def losts = queryList(Context.current.session, "select type(ent), ent.e_id, ${eName} from ${ref.className} ent left join ent.${ref.name} ref where ref is null".toString())
                for (lost in losts) {
                    def lostMap = [_type_: lost[0], e_id: lost[1], name: lost[2]]
                    def parent = Context.current.session.createQuery("select ent from ${opposite.className} ent join ent.${oppositeName} ref where ref.e_id = :e_id").setParameter("e_id", (Long)lostMap.e_id).uniqueResult()
                    if (parent != null) {
                        logger.info("Restore lost reference to parent ${lost._type_}[${lost.e_id}].${ref.name} = ${parent._type_}[${parent.e_id}]")
                        lost.put(ref.name, parent)
                        restored.add(lostMap)
                    }
                    else {
                        toDelete.add(lostMap)
                    }
                }
            }
        }
        def deletedSize = deleted.size()
        while (true) {
            for (d in toDelete) {
                if (deleted.any { it._type_ == d._type_ && it.e_id == d.e_id }) {
                    continue
                }
                def depsCount = 0
                try {
                    getAllDirectDeps(classes, d, []).each { dep ->
                        def container = getContainer(classes, dep)
                        if (d._type_ != container._type_ || d.e_id != container.e_id) {
                            if (deleted.every { it._type_ != container._type_ || it.e_id != container.e_id }) {
                                depsCount += 1
                            }
                        }
                    }
                }
                catch (Throwable t) {
                    logger.error("getAllDirectDeps", t)
                }
                if (depsCount == 0) {
                    deleted.add(d)
                    logger.info("Delete lost object ${d._type_}[${d.e_id}/${d?.name}]")
                    try {
                        Context.current.txSession.delete(Context.current.txSession.get(d._type_, d.e_id))
                        Context.current.commit()
                    }
                    catch (RuntimeException ex) {
                        logger.error("${d._type_}[${d.e_id}/${d?.name}]", ex)
                        Context.current.rollbackResources()
                    }
                } else {
                    skipped.add(d)
                }
            }
            if (deletedSize == deleted.size()) {
                break
            }
            toDelete = skipped
            skipped = []
            deletedSize = deleted.size()
        }
        for (s in skipped) {
            logger.info("Skip referenced lost object ${s._type_}[${s.e_id}/${s?.name}]")
            getAllDirectDeps(classes, s, []).collect {getContainer(classes, it)}. unique {"${it._type_}|${it.e_id}"}.each { dep ->
                logger.info("--> referenced from ${dep._type_}[${dep.e_id}/${dep?.name}]")
            }
        }
        //for (d in deleted) {
        //    logger.info("Delete lost object ${d._type_}[${d.e_id}/${d?.name}]")
        //    session.delete(session.get(d._type_, d.e_id))
        //}
        logger.info("Deleted ${deleted.size()}, skipped ${skipped.size()}, restored ${restored.size()}")
        logger.info("Delete lost objects without parent reference")
        def deletedCount = 0
        for (entityClass in classes.values()) {
            for (ref in entityClass.refs.values().findAll { it.isContainment && it.oppositeName == null}) {
                def query = "SELECT E_ID FROM ${getTableName(classes, ref.refClassName)} WHERE ECONTAINER_CLASS='${ref.className}' AND CAST(E_CONTAINER as INTEGER) NOT IN (SELECT E_ID FROM ${getTableName(classes, ref.className)})"
                logger.info(query.toString())
                def elist = Context.current.session.createSQLQuery(query).addScalar("E_ID").list()
                for (e_id in elist) {
                    classes[ref.refClassName].atts.values().findAll {it.isCollection}.collect {
                        def attTabName = ref.refClassName.tokenize('.').collect{part -> part.toUpperCase()}.join('_') + "_" + it.name.toUpperCase()
                        def idName = ref.refClassName.tokenize('.').last().toUpperCase() + "_" + it.name.toUpperCase() + "_E_ID"
                        def deleteQuery = "DELETE FROM ${attTabName} WHERE ${idName} = ${e_id}".toString()
                        logger.info(deleteQuery)
                        try {
                            deletedCount += Context.current.txSession.createSQLQuery(deleteQuery).executeUpdate()
                            Context.current.commit()
                        }
                        catch (Exception ex) {
                            Context.current.rollbackResources()
                            def entity = [_type_: ref.refClassName, e_id: e_id]
                            try {
                                deletedCount += Context.current.txSession.delete(entity)
                                Context.current.commit()
                            }
                            catch (Exception e) {
                                logger.error(JsonOutput.toJson(entity), e)
                                Context.current.rollbackResources()
                            }
                        }
                    }
                    def dquery = "DELETE FROM ${getTableName(classes, ref.refClassName)} WHERE E_ID = ${e_id}"
                    logger.info("${dquery}")
                    try {
                        deletedCount += Context.current.txSession.createSQLQuery(dquery).executeUpdate()
                        Context.current.commit()
                    }
                    catch (Exception ex) {
                        Context.current.rollbackResources()
                    }
                }
            }
        }
        logger.info("Deleted ${deletedCount} objects without parent reference")
    }

    static Map getMetaInfo() {
        if (metaInfo == null) {
            def pkgs = [:]
            def classes = [:]
            def mi = [pkgInfos:pkgs, classInfos:classes]
            for (pkg in Context.current.contextSvc.teneoSvc.hbds.getEPackages()) {
                def pkgInfo = [name: pkg.name, prefix: pkg.nsPrefix, uri: pkg.nsURI, classInfos: []]
                pkgs[pkg.name] = pkgInfo
                for (eClassifier in pkg.EClassifiers.findAll {it instanceof EClass}) {
                    EClass eClass = eClassifier
                    def className = eClass.getEPackage().nsPrefix + "." + eClass.name
                    def classInfo = [name: className, atts: [:], refs: [:], deps: [], supers: [], pkgInfo: pkgInfo, externalRefCount: 0, externalRefs: [], anns: [:]]
                    classes[className] = classInfo
                    pkgInfo.classInfos.add(classInfo)
                    for (eAnnotation in eClass.EAnnotations) {
                        classInfo.anns[eAnnotation.source] = [:]
                        for (detail in eAnnotation.details) {
                            classInfo.anns[eAnnotation.source][detail.key] = detail.value
                        }
                    }
                    for (eAttr in eClass.EAttributes) {
                        classInfo.atts[eAttr.name] = [name: eAttr.name, EType: eAttr.EType.name, isCollection: eAttr.upperBound == -1 || eAttr.upperBound > 1]
                    }
                    for (eReference in eClass.EReferences) {
                        def refClass = eReference.EReferenceType
                        def isContainment = eReference.containment
                        def isCollection = eReference.upperBound == -1 || eReference.upperBound > 1
                        def oppositeName = eReference.EOpposite?.name
                        def isOppositeContainment = eReference.EOpposite?.containment == true
                        def refClassName = refClass.getEPackage().nsPrefix + "." + refClass.name
                        if (refClassName == "ecore.EObject") {
                            continue
                        }
                        def name = eReference.name
                        def label = "${className}:${eReference.name}${isCollection?"[]":""}${isContainment?"=":"-"}>${refClassName}"
                        def refInfo =[name: name, className: className, refClassName: refClassName, isContainment:isContainment, isCollection:isCollection, label: label, oppositeName: oppositeName, isOppositeContainment: isOppositeContainment]
                        classInfo.refs[name] = refInfo
                    }
                    for (eSuperClass in eClass.ESuperTypes) {
                        def superClassName = eSuperClass.getEPackage().nsPrefix + "." + eSuperClass.name
                        classInfo.supers.add(superClassName)
                    }
                }
            }
            for (classInfo in classes.values()) {
                for (ref in classInfo.refs.values()) {
                    classes[ref.refClassName].deps.add(ref)
                }
            }
            for (classInfo in classes.values()) {
                for (ref in classInfo.refs.values().findAll {!it.isContainment && !it.isOppositeContainment}) {
                    incExternalRefCount(classes, classInfo.name, ref.refClassName)
                }
            }
            for (classInfo in classes.values()) {
                classInfo.hasExternalRefs = fullExternalRefCount(classes, classInfo.name) > 0
            }
            metaInfo = mi
        }
        return metaInfo
    }

    static getAllClassAtts(Map classes, String type, Map atts) {
        for (sType in classes[type].supers) {
            getAllClassAtts(classes, sType, atts)
        }
        atts.putAll(classes[type].atts)
        return atts
    }

    static getAllClassRefs(Map classes, String type, Map refs) {
        for (sType in classes[type].supers) {
            getAllClassRefs(classes, sType, refs)
        }
        refs.putAll(classes[type].refs)
        return refs
    }

    static getAllClassDeps(Map classes, String type, List deps) {
        for (sType in classes[type].supers) {
            getAllClassDeps(classes, sType, deps)
        }
        deps.addAll(classes[type].deps)
        return deps
    }
    private static boolean classContains(Map classes, String type, String refClassName) {
        return classContainsInt(classes, type, refClassName, [])
    }
    private static boolean classContainsInt(Map classes, String type, String refClassName, List falses) {
        for (ref in classes[type].refs.values().findAll {it.isContainment}) {
            if (ref.refClassName == refClassName) {
                return true
            }

        }
        for (f in falses) {
            if (f[0] == type && f[1] == refClassName) {
                return false
            }
        }
        falses.add([type, refClassName])
        for (ref in classes[type].refs.values().findAll {it.isContainment}) {
            if (classContainsInt(classes, ref.refClassName, refClassName, falses)) {
                return true
            }

        }
        for (s in classes[type].supers) {
            if (classContainsInt(classes, s, refClassName, falses)) {
                return true
            }
        }
        for (s in classes[refClassName].supers) {
            if (classContainsInt(classes, type, s, falses)) {
                return true
            }
        }
        return false
    }
    private static void incExternalRefCount (Map classes, String type, String refClassName) {
        if (classContains(classes, type, refClassName)) {
            return
        }
        classes[type].externalRefCount += 1
        classes[type].externalRefs.add(refClassName)
        for (dep in classes[type].deps.findAll {it.isContainment}) {
            incExternalRefCount(classes, dep.className, refClassName)
        }
    }
    private static int fullExternalRefCount (Map classes, String type) {
        def refCount = classes[type].externalRefCount
        for (superClass in classes[type].supers) {
            refCount += fullExternalRefCount(classes, superClass)
        }
        return refCount
    }
}
