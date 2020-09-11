package MetaServer.utils

import org.eclipse.epsilon.emc.emf.EmfModel
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.Context

/**
 * Created by orlov on 18.08.2016.
 */
class EMF {
    public static EmfModel create(String name, Map entity) {
        return createList(name, [entity])
    }
    public static EmfModel createTeneoModel(String name, List entities, List pkgs) {
        EmfModel emfModel = new EmfModel()
        emfModel.modelFileUri = org.eclipse.emf.common.util.URI.createURI("hibernate://?dsname=teneo")
        emfModel.name = name
        emfModel.readOnLoad = false
        emfModel.storedOnDisposal = true
        emfModel.expand = false
        emfModel.metamodelUris = (pkgs + entities.collect { entity ->
            entity._type_.tokenize(".").get(0)
        }).unique().collect{nsPrefix->MPackage.get(nsPrefix).ePackage.nsURI}
        emfModel.loadModelFromUri();
        return emfModel
    }
    public static EmfModel createList(String name, List entities) {
        def emfModel = new EmfModel();
        emfModel.name = name
        emfModel.metamodelUris = entities.collect { entity ->
            entity._type_.tokenize(".").get(0)
        }.unique().collect{nsPrefix->MPackage.get(nsPrefix).ePackage.nsURI}
        emfModel.storedOnDisposal = false
        emfModel.modelFileUri = org.eclipse.emf.common.util.URI.createURI(
            "hibernate://?dsname=teneo&" + entities.withIndex().collect {it, index ->
                "query${index + 1}=from ${it._type_} where e_id=${it.e_id}"
            }.join("&")
        )
        emfModel.loadModelFromUri()
        return emfModel
    }
    public static Object validate(Map entity, String fileName) {
        def emfModel = create("src", entity)
        def problems = []
        Context.current.getContextSvc().epsilonSvc.executeEvl(fileName, [:], [emfModel], problems)
        return [result: (problems.find {it.isCritique == false} == null), problems: problems]
    }

    public static String generate(List entities, String fileName, Map params) {
        def emfModel = createList("S", entities)
        return Context.current.getContextSvc().epsilonSvc.executeEgl(fileName, params, [emfModel])
    }

    public static Object transform(List entities, String fileName, Map params) {
        def sourceModel = createList("S", entities)
        def targetModel = createTeneoModel("T", entities, ["etl"])
        return Context.current.getContextSvc().epsilonSvc.executeEtl(fileName, params, [sourceModel, targetModel])
    }
}
