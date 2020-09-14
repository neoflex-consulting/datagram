package MetaServer.utils

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.eclipse.emf.ecore.*
import org.eclipse.emf.ecore.util.EcoreUtil
import org.emfjson.jackson.annotations.EcoreIdentityInfo
import org.emfjson.jackson.annotations.EcoreTypeInfo
import org.emfjson.jackson.resource.JsonResource
import org.emfjson.jackson.utils.ValueWriter
import org.hibernate.internal.SessionImpl
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.Context
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.emfjson.jackson.resource.JsonResourceFactory;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.emfjson.jackson.module.EMFModule
import ru.neoflex.meta.utils.ECoreUtils;

import java.text.SimpleDateFormat

/**
 * Created by orlov on 22.02.2017.
 */
class Repository {
    private final static Log logger = LogFactory.getLog(Repository.class);

    static getClassQName(EClassifier value) {
        def ePackage = value.getEPackage()
        if (ePackage == null) {
            return null
        }
        return ePackage.getNsPrefix() + "." + value.getName()
    }

    static Object getId(EObject value) {
        Object result = null
        def eResource = value.eResource()
        if (eResource != null && eResource instanceof JsonResource) {
            result = ((JsonResource) eResource).getID(value)
        }
        if (result == null && value instanceof EClassifier) {
            result = getClassQName((EClassifier)value)
        }
        if (result == null) {
            EStructuralFeature feature = value.eClass().getEStructuralFeature("name");
            if (feature != null) {
                result = value.eGet(feature);
            }
        }
        return result;
    }

    static Object info(java.util.Map entity, java.util.Map params) {
        Resource resource = getJsonResource()
        resource.getContents().addAll(ECoreUtils.allPackages())
        ObjectMapper mapper = getJsonMapper()
        mapper.valueToTree(resource);
    }

    public static ObjectMapper getJsonMapper() {
        ObjectMapper mapper = new ObjectMapper();
        EMFModule module = new EMFModule();
        module.setTypeInfo(new EcoreTypeInfo("_type_",
                new ValueWriter<EClass, String>() {
                    @Override
                    public String writeValue(EClass value, SerializerProvider context) {
                        return getClassQName(value);
                    }
                }));
        module.setIdentityInfo(new EcoreIdentityInfo("e_id",
                new ValueWriter<EObject, Object>() {
                    @Override
                    public Object writeValue(EObject value, SerializerProvider context) {
                        return getId(value);
                    }
                }));
        module.setReferenceSerializer(new JsonSerializer<EObject>() {
            @Override
            public void serialize(EObject value, JsonGenerator generator, SerializerProvider provider)
                    throws IOException {
                EClass eClass = value.eClass();
                generator.writeStartObject();
                generator.writeStringField("_type_", eClass.getEPackage().getNsPrefix() + "." + eClass.getName());
                generator.writeStringField("e_id", getId(value));
                generator.writeEndObject();
            }
        });
        mapper.registerModule(module);
        mapper
    }

    public static Resource getJsonResource() {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry()
                .getExtensionToFactoryMap()
                .put("json", new JsonResourceFactory());
        Resource resource = resourceSet.createResource(URI.createFileURI("resources/data.json"));
        resource
    }

    static Object select(java.util.Map entity, java.util.Map params) {
        def sql = params.remove("sql")
        def list = Database.new.select(sql, params)
        Resource resource = getJsonResource()
        resource.getContents().addAll(list);
        //resource.getContents().addAll(EcoreUtil.copyAll(list));
        //for (EObject eObject: list) {
        //    resource.getContents().add(eObject);
        //}
        return getJsonMapper().valueToTree(resource);
    }
}
