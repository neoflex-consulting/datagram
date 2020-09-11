package ru.neoflex.meta.utils;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.epsilon.common.util.StringProperties;
import org.eclipse.epsilon.emc.emf.AbstractEmfModel;
import org.eclipse.epsilon.emc.emf.EmfModel;
import org.eclipse.epsilon.emc.emf.EmfUtil;
import org.eclipse.epsilon.emc.emf.xml.XmlModel;
import org.eclipse.epsilon.eol.exceptions.models.EolModelNotFoundException;
import org.eclipse.epsilon.eol.execute.context.IEolContext;
import org.eclipse.epsilon.eol.models.IModel;
import org.eclipse.epsilon.eol.models.IRelativePathResolver;
import org.eclipse.epsilon.eol.models.Model;
import org.eclipse.epsilon.eol.models.ModelRepository;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ModelManager {

    private IEolContext context;
    private String currentRoot;

	public ModelManager(IEolContext context, String currentRoot) {
		this.context = context;
        this.currentRoot = currentRoot;
	}

    private URI convertVirtualPathToURI(String virtualPath) throws Exception {
        URL url = MetaResource.getURL(currentRoot + "/" + virtualPath);
        return URI.createURI(url.toString());
    }

    public ModelRepository getCurrentModelRepository() {
		return getContext().getModelRepository();
	}
	
	public IModel loadXml(String name, String modelFileName) throws Exception {
		
        XmlModel xmlModel = new XmlModel();

        xmlModel.setName(name);

        StringProperties properties = new StringProperties();
        properties.put(XmlModel.PROPERTY_NAME, name);
        properties.put(XmlModel.PROPERTY_MODEL_FILE, convertVirtualPathToURI(modelFileName));
        properties.put(Model.PROPERTY_READONLOAD, "true");
        properties.put(Model.PROPERTY_STOREONDISPOSAL, "false");
        xmlModel.load(properties, "");
		getCurrentModelRepository().addModel(xmlModel);
		return xmlModel;
	}


    @SuppressWarnings("deprecation")
    public IModel loadModel(String name, String aliases, String modelFileName, String metamodel, boolean expand, boolean metamodelIsFilebased) throws Exception {
        return loadModel(name, aliases, modelFileName, null, metamodel, expand, metamodelIsFilebased, false, false);
    }

	@SuppressWarnings("deprecation")
	public IModel loadModel(String name, String aliases, String modelFileName, String modelURI, String metamodel, boolean expand, boolean metamodelIsFilebased, boolean readOnLoad, boolean storeOnDisposal) throws Exception {

        EmfModel emfModel = new EmfModel();

        StringProperties properties = new StringProperties();
        properties.put(EmfModel.PROPERTY_NAME, name);
        properties.put(EmfModel.PROPERTY_ALIASES, aliases);
        properties.put(EmfModel.PROPERTY_EXPAND, expand + "");
        String uri = modelFileName == null ? modelURI : convertVirtualPathToURI(modelFileName).toString();
        properties.put(EmfModel.PROPERTY_MODEL_URI, uri);
        if (!metamodelIsFilebased) {
            properties.put(EmfModel.PROPERTY_METAMODEL_URI, metamodel);
            properties.put(EmfModel.PROPERTY_IS_METAMODEL_FILE_BASED, "false");
        }
        else {
            properties.put(EmfModel.PROPERTY_FILE_BASED_METAMODEL_URI, convertVirtualPathToURI(metamodel));
            properties.put(EmfModel.PROPERTY_IS_METAMODEL_FILE_BASED, "true");
        }
        properties.put(EmfModel.PROPERTY_READONLOAD, "" + readOnLoad);
        properties.put(EmfModel.PROPERTY_STOREONDISPOSAL, "" + storeOnDisposal);

        emfModel.load(properties, "" );
        getCurrentModelRepository().addModel(emfModel);
        return emfModel;
	}

    public IModel createEmfModel(String name, String model,
                                      String metamodel, boolean readOnLoad, boolean storeOnDisposal)
            throws Exception {
        EmfModel emfModel = new EmfModel();
        StringProperties properties = new StringProperties();
        properties.put(EmfModel.PROPERTY_NAME, name);
        properties.put(EmfModel.PROPERTY_METAMODEL_URI,
                convertVirtualPathToURI(metamodel).toString());
        properties.put(EmfModel.PROPERTY_MODEL_URI,
                convertVirtualPathToURI(model).toString());
        properties.put(EmfModel.PROPERTY_READONLOAD, readOnLoad + "");
        properties.put(EmfModel.PROPERTY_STOREONDISPOSAL,
                storeOnDisposal + "");
        emfModel.load(properties, (IRelativePathResolver) null);
        getCurrentModelRepository().addModel(emfModel);
        return emfModel;
    }

    public IModel createEmfModelByURI(String name, String model,
                                           String metamodel, boolean readOnLoad, boolean storeOnDisposal)
            throws Exception {
        EmfModel emfModel = new EmfModel();
        StringProperties properties = new StringProperties();
        properties.put(EmfModel.PROPERTY_NAME, name);
        properties.put(EmfModel.PROPERTY_METAMODEL_URI, metamodel);
        properties.put(EmfModel.PROPERTY_IS_METAMODEL_FILE_BASED, "false");
        properties.put(EmfModel.PROPERTY_MODEL_URI, model);
        properties.put(EmfModel.PROPERTY_READONLOAD, readOnLoad + "");
        properties.put(EmfModel.PROPERTY_STOREONDISPOSAL,
                storeOnDisposal + "");
        emfModel.load(properties, (IRelativePathResolver) null);
        getCurrentModelRepository().addModel(emfModel);
        return emfModel;
    }
    public void save(String modelName) throws EolModelNotFoundException {
		IModel model = getCurrentModelRepository().getModelByName(modelName);
        model.store();
	}

    public void loadModel(String name, String modelFileName, String metamodelUri) throws Exception {
        loadModel(name, "", modelFileName, metamodelUri, true, false);
    }
    public void loadEmfModel(String name, String modelFileName, String metamodelUri) throws Exception {
        loadModel(name, "", modelFileName, metamodelUri, true, false);
    }


    public void loadModelByFile(String name, String modelFileName, String metamodelFile) throws Exception {
		loadModel(name, "", modelFileName, metamodelFile, true, true);
	}	
	
	public void registerMetamodel(String metamodelFile) throws Exception {
        EmfUtil.register(convertVirtualPathToURI(metamodelFile), EPackage.Registry.INSTANCE);
	}

    public void dispose() {
        List<Resource> toUnload = new ArrayList<>();
        for (IModel model: getContext().getModelRepository().getModels()) {
            if (model instanceof AbstractEmfModel) {
                AbstractEmfModel emfModel = (AbstractEmfModel) model;
                Resource resource = emfModel.getResource();
                if (resource != null && resource.getURI() != null) {
                    toUnload.add(resource);
                }
            }
        }
        getContext().getModelRepository().dispose();
        for (Resource resource: toUnload) {
            resource.unload();
        }
    }

    public IEolContext getContext() {
        return context;
    }

    public String getCurrentRoot() {
        return currentRoot;
    }
}
