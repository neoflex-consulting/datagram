package MetaServer.etl

import MetaServer.utils.EMF;
import ru.neoflex.meta.utils.Context;

/* protected region MetaServer.etlSelection.inport on begin */
import MetaServer.utils.Scala
import ru.neoflex.meta.model.Database
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory;
import MetaServer.rt.LivyServer;
import org.eclipse.epsilon.emc.emf.EmfModel;
import org.eclipse.epsilon.common.util.StringProperties;
import groovy.json.JsonSlurper;
import org.apache.avro.Schema
import ru.neoflex.meta.utils.JSONHelper

/* protected region MetaServer.etlSelection.inport end */

class SchemeDataSet {
    /* protected region MetaServer.etlSelection.statics on begin */


    private final static Log logger = LogFactory.getLog(SchemeDataSet.class);

    private static boolean isCollectionOrArray(object) {
        [Collection, Object[]].any { it.isAssignableFrom(object.getClass()) }
    }

    private final static avroSimpleTypes = ["string", "double", "float", "int", "boolean", "long", "bytes"]
    private final static avroComplexTypes = ["array", "record", "map", "union"]

    private static avroDomainToFieldtype(typeString) {
        def result
        switch (typeString) {
            case "string":
                result = 'STRING'
                break
            case "double":
                result = 'DOUBLE'
                break
            case "float":
                result = 'FLOAT'
                break
            case "int":
                result = 'INTEGER'
                break
            case "boolean":
                result = 'BOOLEAN'
                break
            case "long":
                result = 'LONG'
                break
            case "bytes":
                result = 'BINARY'
                break
            default:
                result = '______'
                break
        }
        result

    }

    protected static String getSimpleType(Schema schema) {
        def result = null;
        switch (schema.type) {
            case Schema.Type.ARRAY:
                result = null;
                break
            case Schema.Type.MAP:
                result = null;
                break
            case Schema.Type.RECORD:
                result = null;
                break
            case Schema.Type.UNION:
                result = false;
                def types = schema.getTypes()
                if (types.size() == 2) {
                    def inSimples = false
                    def inComplex = false
                    def val = null
                    types.each {
                        if (avroSimpleTypes.contains(it.name)) {
                            val = it.name
                            inSimples = true
                        }
                        if (avroComplexTypes.contains(it.name)) {
                            inComplex = true
                        }
                    }
                    if (inSimples && !inComplex) {
                        result = val;
                    }
                }
                break
            default:
                result = schema.name
        }
        return result
    }

    protected static Map processField(Schema schema, db) {
        def result = [:]
        def simpeTypeName = getSimpleType(schema)
        if (simpeTypeName != null && !simpeTypeName.equalsIgnoreCase("false")) {
            result.dataTypeDomain = JSONHelper.getEnumerator("teneo", "dataset.Field", "dataTypeDomain", avroDomainToFieldtype(simpeTypeName))
        } else {
            if (schema.type == Schema.Type.UNION) {
                result.dataTypeDomain = JSONHelper.getEnumerator("teneo", "dataset.Field", "dataTypeDomain", avroDomainToFieldtype('______'))
                for (t in schema.types) {
                    if (t.type == Schema.Type.RECORD) {
                        def struct = _fillStruct(db, t)
                        result.domainStructure = struct
                    }
                    if(t.type == Schema.Type.ARRAY){
                        def arrayFieldDef =  _fillArray(t, db)
                        result.domainStructure = arrayFieldDef
                    }
                }
            }

            if (schema.type == Schema.Type.RECORD) {
                result.dataTypeDomain = JSONHelper.getEnumerator("teneo", "dataset.Field", "dataTypeDomain", avroDomainToFieldtype('______'))
                def struct = _fillStruct(db, schema)
                result.domainStructure = struct
            }
            if (schema.type == Schema.Type.ARRAY) {
                result.dataTypeDomain = JSONHelper.getEnumerator("teneo", "dataset.Field", "dataTypeDomain", avroDomainToFieldtype('______'))
                def arrayFieldDef = _fillArray(schema, db);
                result.domainStructure = arrayFieldDef
            }
        }
        return result
    }

    private static Object _fillStruct(db, Schema t) {
        def internalStructure = db.instantiate("dataset.Structure", [fields: t.hasProperty("fields") != null && t.fields.size() > 0 ? (List) createFields(t, db) : []])
        def struct = db.instantiate("dataset.StructType", [internalStructure: internalStructure])
        return struct
    }

    private static Object _fillArray(Schema t, db) {
        def simpleType = getSimpleType(t.getElementType())
        def elementType = null
        def arrayField = [:]
        if (simpleType != null) {
            elementType = db.instantiate("dataset.ScalarType", [dataTypeDomain: JSONHelper.getEnumerator("teneo", "dataset.Field", "dataTypeDomain", avroDomainToFieldtype(simpleType))])
        } else {
            if (t.getElementType().type == Schema.Type.RECORD) {
                def internalStructure = db.instantiate("dataset.Structure", [fields: (List) createFields(t.getElementType(), db)])
                elementType = db.instantiate("dataset.StructType", [internalStructure: internalStructure])
            }
            if (t.getElementType().type == Schema.Type.ARRAY) {
                elementType = db.instantiate("dataset.ArrayType", [elementType: processField(t.getElementType(), db)])
            }
        }
        def arrayFieldDef = db.instantiate("dataset.ArrayType", [elementType: elementType])
        return arrayFieldDef
    }

    protected static Object[] createFields(avroScheme, db) {
        def datasetFields = []
        for (field in avroScheme.fields) {
            def name = field.name
            def dsfield = processField(field.schema, db)
            dsfield.name = name
            def newField = db.instantiate("dataset.Field", dsfield)
            datasetFields.add(newField)
        }
        return datasetFields
    }

    public static Object generateFromString(Map entity, Map params = null) {
        def db = Database.new
        def dataset = db.session.createQuery("from etl.SchemeDataSet where e_id = :e_id").setParameter("e_id", entity.e_id.longValue()).uniqueResult()

        def stringScheme = dataset.schemeString

        def jsonSlurper = new JsonSlurper()
        def schemeObject = jsonSlurper.parseText(stringScheme)
        def parser = new Schema.Parser()
        def avroScheme = parser.parse(stringScheme)
        def datasetFields = createFields(avroScheme, db)

        dataset.schemeDataset.fields.clear()
        dataset.schemeDataset.fields.addAll(datasetFields)
        db.save(dataset)

        return [result: "create " + datasetFields.size() + " fields"]
    }

    /* protected region MetaServer.etlSelection.statics end */
}
