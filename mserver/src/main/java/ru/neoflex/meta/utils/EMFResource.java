package ru.neoflex.meta.utils;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.emf.teneo.hibernate.HbDataStore;
import org.eclipse.emf.teneo.hibernate.resource.HibernateResource;
import org.eclipse.epsilon.common.parse.problem.ParseProblem;
import org.eclipse.epsilon.emc.emf.EmfUtil;
import org.eclipse.epsilon.hutn.HutnModule;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by orlov on 14.06.2015.
 */
public class EMFResource {
    public static List<EPackage> registerEcorePackages(File modulesDir) {
        List<EPackage> ePackages = new LinkedList<>();
        for (File module: modulesDir.listFiles()) {
            if (module.isFile() && module.getName().endsWith(".ecore")) {
                ePackages.addAll(registerPackages(module));
            }
        }
        return ePackages;
    }

    public static File generateEcoreFromEmf(File modulesDir) {
        for (File module: modulesDir.listFiles()) {
            if (module.isFile() && module.getName().endsWith(".emf")) {
                File ecoreFile = new File(module.getAbsolutePath().replaceAll("\\.emf$", ".ecore"));
                if (!ecoreFile.isFile() || ecoreFile.lastModified() < module.lastModified()) {
                    Emfatic.emf2ecore(module);
                }
            }
        }
        return modulesDir;
    }

    public static void loadDirContentToResource(File dir, Resource res) {
        for (File resourceFile: dir.listFiles()) {
            if (resourceFile.isFile()) {
                String name = resourceFile.getName();
                if (name.endsWith(".xmi") || name.endsWith(".model") || name.endsWith(".ecore")) {
                    loadFileToResource(resourceFile, res);
                    resourceFile.renameTo(new File(resourceFile.getAbsolutePath() + ".loaded"));
                }
            }
        }
    }

    public static void loadFileToResource(File resourceFile, Resource resourceTo) {
        Resource resource = loadResource(resourceFile, resourceTo.getResourceSet());
        copyContent(resource, resourceTo);
        try {
            resourceTo.save(null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Resource getTeneoResource(HbDataStore hbds) {
        return getTeneoResource(hbds, (String)null);
    }

    public static Resource getTeneoResource(HbDataStore hbds, Map params) {
        String query = "";
        for (Object key : params.keySet()) {
            query += "&" + key + "=" + params.get(key);
        }
        return getTeneoResource(hbds, query);
    }

    public static Resource getTeneoResource(HbDataStore hbds, String query) {
        return getTeneoResource(hbds.getName(), query);
    }

    public static Resource getTeneoResource(ResourceSet resourceSet, String name, String query, boolean load) {
        String uriStr = "hibernate://?" + HibernateResource.DS_NAME_PARAM + "=" + name;
        if (query != null && query.length() > 0) {
            if (query.charAt(0) != '&')
                uriStr += "&";
            uriStr += query;
        }
        final URI uri = URI.createURI(uriStr);
        final Resource res = resourceSet.createResource(uri);
        try {
            if (load) {
                res.load(Collections.EMPTY_MAP);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return res;
    }

    public static Resource getTeneoResource(String name, String query) {
        ResourceSet resourceSet = new ResourceSetImpl();
        return getTeneoResource(resourceSet, name, query, true);
    }

    public static void copyContent(Resource from, Resource to) {
        //to.getContents().addAll(EcoreUtil.copyAll(from.getContents()));
        to.getContents().addAll(from.getContents());
    }

    public static List<EPackage> registerPackages(File file) {
        return registerPackages(URI.createFileURI(file.getAbsolutePath()));
    }

    public static List<EPackage> registerPackages(URI resource) {
        try {
            return EmfUtil.register(resource, EPackage.Registry.INSTANCE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Resource ecore2xmi(File ecoreFile) {
        ResourceSet resourceSet = new ResourceSetImpl();
        String ecorePath = ecoreFile.getAbsolutePath();
        if (!ecorePath.endsWith(".ecore")) {
            throw new RuntimeException("not .ecore file extension");
        }
        String xmiPath = ecorePath.replaceAll("\\.ecore$", ".xmi");
        URI uri = URI.createFileURI(xmiPath);
        Resource xmiResource = resourceSet.createResource(uri);
        loadFileToResource(ecoreFile, xmiResource);
        return xmiResource;
    }

    public static Resource loadResource(File resPath, ResourceSet resourceSet) {
        return getResource(resPath, resourceSet, true);
    }

    public static Resource getResource(File resPath, ResourceSet resourceSet, boolean load) {
        if (resourceSet == null) {
            resourceSet = createResourceSet();
        }
        URI uri = URI.createFileURI(resPath.getAbsolutePath());
        return resourceSet.getResource(uri, load);
    }

    public static ResourceSet createResourceSet() {
        ResourceSet resourceSet = new ResourceSetImpl();
        Map<String, Object> options = resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap();
        options.put("ecore", new EcoreResourceFactoryImpl());
        options.put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
        return resourceSet;
    }

    public static Resource createResource(File resPath, ResourceSet resourceSet) {
        Map<String, Object> options = resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap();
        options.put("ecore", new EcoreResourceFactoryImpl());
        options.put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
        URI uri = URI.createFileURI(resPath.getAbsolutePath());
        return resourceSet.createResource(uri);
    }

    public static File convertHutnResource(File resPath) {
        return convertHutnResource(resPath, resPath.getParentFile());
    }

    public static File convertHutnResource(File resPath, File modulesDir) {
        try {
            HutnModule module = new HutnModule();
            module.parse(resPath);
            if (module.getParseProblems().size() > 0) {
                String message = "Syntax error(s) in ";
                for (ParseProblem problem : module.getParseProblems()) {
                    message += problem.toString() + "\n";
                }
                throw new RuntimeException(message);
            }
            String xmiName = resPath.getName().replaceAll("\\.hutn$", ".xmi");
            module.storeEmfModel(resPath.getParentFile(), xmiName, modulesDir.getAbsolutePath());
            File result = new File(resPath.getParentFile(), xmiName);
            assert result.isFile();
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void generateXmiFromHutn(File dir, File modulesDir) {
        for (File resourceFile: dir.listFiles()) {
            if (resourceFile.isFile() && resourceFile.getName().endsWith(".hutn")) {
                convertHutnResource(resourceFile, modulesDir);
                resourceFile.renameTo(new File(resourceFile.getAbsolutePath() + ".generated"));
            }
        }
    }

    public static void generateXmiFromEcore(File dir, File modulesDir) {
        for (File resourceFile: dir.listFiles()) {
            if (resourceFile.isFile() && resourceFile.getName().endsWith(".ecore")) {
                Resource xmiRes = ecore2xmi(resourceFile);
                resourceFile.renameTo(new File(resourceFile.getAbsolutePath() + ".generated"));
            }
        }
    }
}
