package ru.neoflex.meta.svc;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.epsilon.ecl.EclModule;
import org.eclipse.epsilon.egl.EglFileGeneratingTemplateFactory;
import org.eclipse.epsilon.egl.EgxModule;
import org.eclipse.epsilon.emc.emf.EmfMetaModel;
import org.eclipse.epsilon.emc.emf.EmfModel;
import org.eclipse.epsilon.eol.EolModule;
import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;
import org.eclipse.epsilon.eol.models.IModel;
import org.eclipse.epsilon.etl.EtlModule;
import org.eclipse.epsilon.flock.FlockModule;
import org.eclipse.epsilon.flock.execution.exceptions.FlockUnsupportedModelException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import ru.neoflex.meta.utils.EMFResource;
import ru.neoflex.meta.utils.Emfatic;
import ru.neoflex.meta.utils.MetaResource;

/**
 * Created by orlov on 08.06.2015.
 */
@Service("ru.neoflex.meta.svc.MSpaceSvc")
public class MSpaceSvc extends BaseSvc {

    public static final String MSPACE_META_SERVER_MODEL = "cim/MetaServer/pim/models/MetaServer.model";
    private final static Log logger = LogFactory.getLog(MSpaceSvc.class);

    @Autowired
    private TeneoSvc teneoSvc;

    @Autowired
    private EpsilonSvc epsilonSvc;

    @Autowired
    private AntSvc antSvc;

    @Autowired
    private MavenSvc mavenSvc;

    @Autowired
    private ScriptSvc scriptSvc;

    private Resource mServerResource;

    private Object get(EObject o, String name) {
        return o.eGet(o.eClass().getEStructuralFeature(name));
    }

    private void set(EObject o, String name, Object value) {
        o.eSet(o.eClass().getEStructuralFeature(name), value);
    }

    @PostConstruct
    void init() {
        EPackage.Registry registry = EPackage.Registry.INSTANCE;
        registry.clear();
        registry.put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);
        List<EPackage> persistent = new LinkedList<>();
        org.eclipse.emf.common.util.URI emfURI = org.eclipse.emf.common.util.URI.createURI(MetaResource.getURL("pim/mspace/mspace.ecore").toString());
        List<EPackage> mspaces = EMFResource.registerPackages(emfURI);
        if (mspaces.size() != 1) throw new RuntimeException("mspace.ecore not found");
        EObject metaServer = getMetaServerFromXMI();
        try {
            initGroovy((EList<EObject>) get(metaServer, "groovyScriptBases"));
            for (EObject model: (EList<EObject>)get(metaServer, "registerOnStartup")) {
                processRegisterModel(model, null);
            }
            for (EObject model: (EList<EObject>)get(metaServer, "persistent")) {
                processRegisterModel(model, persistent);
            }

            getTeneoSvc().initialize(persistent);

            for (EObject script : (EList<EObject>)get(metaServer, "scripts")) {
                processStartupScript(script);
            }
        }
        finally {
            mServerResource.unload();
            mServerResource = null;
        }
    }

    @PreDestroy
    void fini() {
        EObject metaServer = getMetaServerFromXMI();
        for (EObject script : (EList<EObject>)get(metaServer, "scripts")) {
            Boolean runOnShutdown = (Boolean) get(script, "runOnShutdown");
            if (runOnShutdown) {
                processScript(script);
            }
        }
    }

    private EObject getMetaServerFromXMI() {
        mServerResource = loadXmiResource(MSPACE_META_SERVER_MODEL);
        if (mServerResource == null) throw new RuntimeException("MetaServer.model not found");
        EList<EObject> eObjects = mServerResource.getContents();
        if (eObjects.size() != 1) throw new RuntimeException("MetaServer not found");
        return eObjects.get(0);
    }

    private void processRegisterModel(EObject model, List<EPackage> persistent) {
        URI modelURI = getModelURI(model);
        List<EPackage> ps = EMFResource.registerPackages(modelURI);
        if (persistent != null) {
            persistent.addAll(ps);
        }
    }

    private Object processStartupScript(EObject script) {
        Boolean runOnStatrup = (Boolean) get(script, "runOnStatrup");
        Boolean runOnce = (Boolean) get(script, "runOnce");
        Object result = null;
        if (runOnStatrup) {
            result = processScript(script);
            if (runOnce) {
                set(script, "runOnStatrup", false);
                try {
                    mServerResource.save(null);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return result;
    }

    public Object processScript(String name) {
        EObject metaServer = getMetaServerFromXMI();
        try {
            for (EObject script : (EList<EObject>)get(metaServer, "scripts")) {
                String sName = (String) get(script, "name");
                if (sName.equals(name)) {
                    return processScript(script);
                }
            }
            throw new RuntimeException("Script " + name + " not found");
        }
        finally {
            mServerResource.unload();
            mServerResource = null;

        }
    }

    public Object processScript(EObject script) {
        try {
            Map params = getStringParams(script);
            params.put("mspaceRoot", getMSpaceDir().getAbsolutePath());
            if (isKindOf(script, "http://www.neoflex.ru/meta/mspace", "GroovyScript")) {
                return runGroovyScript(script, params);
            } else if (isKindOf(script, "http://www.neoflex.ru/meta/mspace", "EpsilonScript")) {
                return runEpsilonScript(script, params);
            } else if (isKindOf(script, "http://www.neoflex.ru/meta/mspace", "AntScript")) {
                return runAntScript(script, params);
            } else if (isKindOf(script, "http://www.neoflex.ru/meta/mspace", "MavenScript")) {
                return runMavenScript(script, params);
            } else {
                throw new RuntimeException("dont know, how to execute " + script.eClass().getName());
            }
        }
        catch (Throwable th) {
            logger.error("Script " + get(script, "name"), th);
        }
        return null;
    }

    private Object runMavenScript(EObject script, Map params) {
        return mavenSvc.run(fileFromEObject(script), (String) get(script, "goals"), (String) get(script, "home"),
                (String) get(script, "repository"), (String) get(script, "site"), params);
    }

    private Object runAntScript(EObject script, Map params) {
        return antSvc.run(fileFromEObject(script), params);
    }

    private boolean isKindOf(EObject eObject, String nsURI, String typeName) {
        EPackage ePackage = EPackage.Registry.INSTANCE.getEPackage(nsURI);
        if (ePackage == null)
            return false;
        EClass objectClass = (EClass)ePackage.getEClassifier(eObject.eClass().getName());
        if (objectClass == null)
            return false;
        EClass typeClass = (EClass)ePackage.getEClassifier(typeName);
        if (typeClass == null)
            return false;
        return typeClass.isSuperTypeOf(objectClass);
    }

    private void initGroovy(EList<EObject> groovyScriptBases) {
        List<URL> bases = new LinkedList<>();
        for (EObject gsb: groovyScriptBases) {
            try {
                bases.add(uriFromEObject(gsb).toURL());
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
        scriptSvc.configure(bases.toArray(new URL[0]));
    }

    private java.net.URI uriFromEObject(EObject file) {
        String name = (String) get(file, "path");
        String fileBase = ((EEnumLiteral) get(file, "fileBase")).getName();
        return getUri(name, fileBase);
    }

    private java.net.URI getUri(String name, String fileBase) {
        if ("MSPACE".equals(fileBase)) {
            return MetaResource.getURI(name);
        }
        return (new File(name)).toURI();
    }

    private File fileFromEObject(EObject file) {
        String name = (String) get(file, "path");
        String fileBase = ((EEnumLiteral) get(file, "fileBase")).getName();
        if ("MSPACE".equals(fileBase)) {
            return new File(getMSpaceDir(), name);
        }
        return new File(name);
    }

    private Object runEpsilonScript(EObject script, Map params) {
        List<IModel> models = loadEpsilonModels(script);
        java.net.URI scriptURI = uriFromEObject(script);
        if (isKindOf(script, "http://www.neoflex.ru/meta/mspace", "FlockScript")) {
            FlockModule module = new FlockModule();
            try {
                IModel source = loadEpsilonModel((EObject) get(script, "source"));
                models.add(source);
                IModel target = loadEpsilonModel((EObject) get(script, "target"));
                models.add(target);
                module.getContext().setOriginalModel(source);
                module.getContext().setMigratedModel(target);
            } catch (FlockUnsupportedModelException e) {
                throw new RuntimeException(e);
            }
            return getEpsilonSvc().execute(module, scriptURI, params, models, true);
        }
        else if (isKindOf(script, "http://www.neoflex.ru/meta/mspace", "EolScript")) {
            return getEpsilonSvc().execute(new EolModule(), scriptURI, params, models, true);
        }
        else if (isKindOf(script, "http://www.neoflex.ru/meta/mspace", "EtlScript")) {
            return getEpsilonSvc().execute(new EtlModule(), scriptURI, params, models, true);
        }
        else if (isKindOf(script, "http://www.neoflex.ru/meta/mspace", "EclScript")) {
            return getEpsilonSvc().execute(new EclModule(), scriptURI, params, models, true);
        }
        else if (isKindOf(script, "http://www.neoflex.ru/meta/mspace", "EgxScript")) {
            return getEpsilonSvc().execute(new EgxModule(new EglFileGeneratingTemplateFactory()), scriptURI, params, models, true);
        }
        else if (isKindOf(script, "http://www.neoflex.ru/meta/mspace", "EglScript")) {
            return getEpsilonSvc().executeEgl(scriptURI, MetaResource.parentDirPath(scriptURI), params, models);
        }
        else {
            throw new RuntimeException("dont know, how to execute " + script.eClass().getName());
        }
    }

    private List<IModel> loadEpsilonModels(EObject script) {
        List<IModel> models = new LinkedList<>();
        for (EObject param: (EList<EObject>)get(script, "models")) {
            IModel model = loadEpsilonModel(param);
            if (model != null) {
                models.add(model);
            }
        }
        return models;
    }

    private IModel loadEpsilonModel(EObject param) {
        IModel model = null;
        if (isKindOf(param, "http://www.neoflex.ru/meta/mspace", "EmfModelParameter")) {
            EObject modelObject = (EObject) get(param, "emfModel");
            if (isKindOf(modelObject, "http://www.neoflex.ru/meta/mspace", "FileModel")) {
                model = loadFileModel(param, modelObject);
            }
            else if (isKindOf(modelObject, "http://www.neoflex.ru/meta/mspace", "URIModel")) {
                model = loadURIModel(param, modelObject);
            }
        }
        else if (isKindOf(param, "http://www.neoflex.ru/meta/mspace", "RegisteredModelParameter")) {
            EObject modelObject = (EObject) get(param, "registeredModel");
            model = loadRegisteredModel(param, modelObject);
        }
        return model;
    }

    private IModel loadRegisteredModel(EObject param, EObject modelObject) {
        String nsURI = (String) get(modelObject, "nsURI");
        String name = (String) get(param, "name");
        EmfMetaModel model = new EmfMetaModel(nsURI);
        model.setName(name);
        try {
            model.loadModel();
        } catch (EolModelLoadingException e) {
            throw new RuntimeException(e);
        }
        return model;
    }

    private IModel loadEmfModel(EObject param, EObject modelObject, URI uri) {
        String name = (String) get(param, "name");
        Boolean read = (Boolean) get(param, "read");
        Boolean store = (Boolean) get(param, "store");
        Boolean expand = (Boolean) get(param, "expand");
        EmfModel model = new EmfModel();
        model.setModelFileUri(uri);
        model.setName(name);
        model.setReadOnLoad(read);
        model.setStoredOnDisposal(store);
        model.setExpand(expand);
        List<String> mmUris = new LinkedList<>();
        for (EObject mmUri: (EList<EObject>)get(modelObject, "mmUris")) {
            String mmUriStr = (String) get(mmUri, "uri");
            mmUris.add(mmUriStr);
        }
        model.setMetamodelUris(mmUris);
        try {
            model.loadModelFromUri();
        } catch (EolModelLoadingException e) {
            throw new RuntimeException(e);
        }
        return model;
    }

    private IModel loadURIModel(EObject param, EObject modelObject) {
        String uri = (String) get(modelObject, "uri");
        return loadEmfModel(param, modelObject, URI.createURI(uri));
    }

    private IModel loadFileModel(EObject param, EObject modelObject) {
        URI modelURI = getModelURI(modelObject);
        return loadEmfModel(param, modelObject, modelURI);
    }

    private URI getModelURI(EObject model) {
        String name = (String) get(model, "path");
        String fileBase = ((EEnumLiteral) get(model, "fileBase")).getName();
        String fileModelType = ((EEnumLiteral) get(model, "fileModelType")).getName();
        return getModelUri(name, fileBase, fileModelType);
    }

    private URI getModelUri(String name, String fileBase, String fileModelType) {
        if ("XMI".equals(fileModelType)) {
            return URI.createURI(getUri(name, fileBase).toString());
        }
        File modelFile = null;
        if ("MSPACE".equals(fileBase)) {
            modelFile = MetaResource.export(name, getDeployDir(), name + ".exportCurrentBranch");
        }
        else {
            modelFile = new File(name);
        }
        if ("HUTN".equals(fileModelType)) {
            modelFile = EMFResource.convertHutnResource(modelFile);
        }
        else if ("EMF".equals(fileModelType)) {
            modelFile = Emfatic.emf2ecore(modelFile);
        }
        return URI.createFileURI(modelFile.getAbsolutePath());
    }

    private Object runGroovyScript(EObject script, Map params) {
        String path = (String) get(script, "path");
        return scriptSvc.run(path, params);
    }

    private Map getStringParams(EObject script) {
        Map params = new HashMap();
        for (EObject param: (EList<EObject>)get(script, "parameters")) {
            params.put(get(param, "name"), get(param, "value"));
        }
        return params;
    }

    private Resource loadXmiResource(String path) {
        ResourceSet resourceSet = new ResourceSetImpl();
        URI uri = getModelUri(path, "MSPACE", "XMI");
        return resourceSet.getResource(uri, true);
    }

    public TeneoSvc getTeneoSvc() {
        return teneoSvc;
    }

    public EpsilonSvc getEpsilonSvc() {
        return epsilonSvc;
    }
}