package MetaServer.sse

import MetaServer.rt.LivyServer
import com.google.common.base.Strings
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.eclipse.emf.common.util.Enumerator
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.JSONHelper

// TODO поддержка link paragrpah
// TODO протянуть информацию о количестве данных для показа

class Paragraph {
    private static final Log logger = LogFactory.getLog(Paragraph.class)
    private static final def supportedInterpreters = ["SPARK", "SQL", "PYTHON", "R"]

    private static final def NEW = JSONHelper.getEnumerator("teneo", "sse.Paragraph", "status", "NEW")
    private static final def PENDING = JSONHelper.getEnumerator("teneo", "sse.Paragraph", "status", "PENDING")
    private static final def IN_PROGRESS = JSONHelper.getEnumerator("teneo", "sse.Paragraph", "status", "IN_PROGRESS")
    private static final def SUCCESS = JSONHelper.getEnumerator("teneo", "sse.Paragraph", "status", "SUCCESS")
    private static final def ERROR = JSONHelper.getEnumerator("teneo", "sse.Paragraph", "status", "ERROR")

    static Map run(Map entity, Map params = null) {
        // like monads Context -> Either (Error, Context)
        // checkId -> checkId -> get P -> check intp -> check text -> get livy -> check livy ->
        //   set status -> get code -> run code -> check result -> parse result

        def result = validate({ checkId(entity.e_id) }, "Cannot find e_id of entity")
        if (result.status != "OK") {
            return result
        }

        result = validate({ checkId(params?.nb_id) }, "Cannot find id of notebook")
        if (result.status != "OK") {
            return result
        }

        def db = Database.new
        BaseParagraph paragraph = BaseParagraph.instantiate(entity)

        result = validate({ supportedInterpreters.contains(paragraph.getInterpreter()) },
                "Unsupported interpreter ${paragraph.getInterpreter()}")
        if (result.status != "OK") {
            return result
        }

        result = validate({ !Strings.isNullOrEmpty(paragraph.getText()) },
                "Nothing to run")
        if (result.status != "OK") {
            return result
        }

        def nb = db.get(params.nb_type ?: "sse.Notebook", Long.parseLong((String) params.nb_id))

        paragraph.setStatus(PENDING)
        paragraph.setResult(null)
        paragraph.save()

        Notebook.submit(nb, paragraph, params)

        return [status  : "OK",
                problems: [],
                entity  : toJSON(paragraph.getEntity())]
    }

    private static boolean checkId(Object e_id) {
        if (e_id == null) return false
        if (!e_id instanceof String) return false
        try {
            Long.parseLong((String) e_id)
        } catch (NumberFormatException e) {
            return false
        }
        return true
    }

    private static Enumerator makeCodeBodyEnum(String propName, String value) {
        return JSONHelper.getEnumerator("teneo", "sse.CodeBody", propName, value)
    }

    private static Map toJSON(Map paragraph) {
        return JSONHelper.toJSON("teneo", "sse.Paragraph", paragraph)
    }

    private static Map parseTableResult(Map output) {
        def result = Database.new.instantiate("sse.TableResult")

        def data = [columns: [], rows: []]
        if (output instanceof Map) {
            def rawData = output["text/plain"]
            if (!Strings.isNullOrEmpty(rawData)) {
                def parsedJson = rawData =~ /(?ms).*^(\{.*\})$.*/
                if (parsedJson.matches()) {
                    rawData = parsedJson.group(1)
                }
                else {
                    rawData = "{}"
                }
                def jsonData = JSONHelper.string2map(rawData)
                def schema = jsonData.schema
                for (f in schema.fields) {
                    def colParams = fieldSchemaParse(f)
                    def column = AbstractDataset.createColumn(Database.new, colParams)
                    data.columns.add(column)
                }

                data.rows = jsonData.data
            }
        }
        result.columns = data.columns
        result.rowsData = JSONHelper.pp(data.rows)
        return result
    }

    private static Map<String, Object> fieldSchemaParse(Map<String, Object> field) {
        def colParams = [columnName: field.name,
                         isNullable: field.nullable]

        def isScalarType = field.type instanceof String && !(field.type in ["struct", "array"])
        def isArrayType = field.type instanceof Map && field.type.type == "array"
        isArrayType = isArrayType || (field.type instanceof String && field.type == "array")
        def isStructType = field.type instanceof Map && field.type.type == "struct"
        isStructType = isStructType || (field.type instanceof String && field.type == "struct")

        if (isScalarType) {
            colParams.columnType = "ScalarType"
            colParams.nativeType = field.type
            colParams.typ = HiveDataset.hive2DataType[field.type.toUpperCase()]
        }

        if (isStructType) {
            colParams.columnType = "StructType"
            colParams.typ = "STRUCT"
            colParams.columns = []
            for (ci in field.fields) {
                colParams.columns.add(fieldSchemaParse(ci))
            }
        }

        if (isArrayType) {
            colParams.columnType = "ArrayType"
            colParams.typ = "ARRAY"
            colParams.elementType = fieldSchemaParse(field.type.elementType)
        }

        return colParams
    }

    private static Map parseTextResult(Map output) {
        def result = Database.new.instantiate("sse.TextResult")
        result.data = ""
        if (output instanceof Map) {
            def imgKey = "image/png"
            def textKey = "text/plain"
            if (output.keySet().contains(imgKey)) {
                result = Database.new.instantiate("sse.ImageResult")
                result.mimeType = imgKey
                result.base64data = output[imgKey]
            } else if (output.keySet().contains(textKey)) {
                result.data = output[textKey]
            }
        }
        return result
    }

    private static Map<String, String> genCode(String text, String interpreter, int batchSize) {
        Objects.requireNonNull(text)
        Objects.requireNonNull(interpreter)

        def fetchCount = batchSize > 0 ? "take(${batchSize})" : "collect()"

        if (interpreter.toUpperCase() == "SQL") {
            def code = """
def df = spark.sql(\"\"\"${text}\"\"\")
val schema = df.schema.json
val data = df.toJSON.${fetchCount}.mkString(\"[\", \",\", \"]\")
println(s\"\"\"{\"schema\":\$schema, \"data\":\$data}\"\"\")
"""
            return [code: code, kind: "spark"]
        } else {
            return [code: text, kind: interpreter.toLowerCase()]
        }
    }

    private static def validate(expr, String errMsg) {
        def isValid = expr.call()
        if (!isValid) {
            logger.error(errMsg)
            return [status  : "ERROR",
                    problems: [errMsg],
                    entity  : [:]]
        } else {
            return [status: "OK", problems: [], entity: [:]]
        }
    }

    static abstract class BaseParagraph {
        protected final Map<String, Object> originalEntity
        protected Map<String, Object> databaseEntity

        protected BaseParagraph(Map<String, Object> entity) {
            Database db = Database.new
            this.originalEntity = entity
            this.databaseEntity = db.get(entity)
            copyRequestToDb()
        }

        static BaseParagraph instantiate(Map<String, Object> entity) {
            if (entity.body._type_ == "sse.CodeBody") {
                return new CodeParagraph(entity)
            } else if (entity.body._type_ == "sse.LinkBody") {
                return new LinkParagraph(entity)
            } else {
                throw new IllegalArgumentException("Unsupported entity type " + entity._type_)
            }
        }

        protected abstract void copyRequestToDb()

        abstract String getInterpreter()

        abstract String getText()

        void setStatus(status) {
            this.databaseEntity.status = status
        }

        void setResult(Map<String, Object> result) {
            this.databaseEntity.result = result
        }

        void save() {
            Database db = Database.new
            db.saveOrUpdate(this.databaseEntity)
            db.commit()
        }

        Map<String, Object> getEntity() {
            return this.databaseEntity
        }

        void run(Map<String, Object> livy, int sessionId = 0, Map<String, Object> params) {
            Database.new.clear()

            this.databaseEntity = Database.new.get(this.originalEntity)

            this.setStatus(IN_PROGRESS)
            this.setResult(null)
            this.save()

            def batchSize = Integer.valueOf(params.get("batchSize", "-1") as String)

            def code = genCode(this.getText(), this.getInterpreter(), batchSize)

            def result
            try {
                result = LivyServer.executeStatementAndWait(sessionId, code.code, logger, livy, code.kind)
            } catch (Throwable e) {
                logger.error(e.getMessage())

                this.setErrorResult("Livy server error", "", e.getMessage())
                return
            }

            if (result.output.status == 'error') {
                logger.error("${result.output.ename}: ${result.output.evalue}, ${result.output.traceback}")
                def traceback = (result.output.traceback ?: []).join("\n")
                this.setErrorResult(result.output.ename, result.output.evalue, traceback)
                return
            }

            this.setStatus(SUCCESS)
            this.setResult(this.getInterpreter() == "SQL" ?
                    parseTableResult(result.output.data)
                    : parseTextResult(result.output.data))
            this.save()
        }

        void setErrorResult(String ename, String evalue, String errorData) {
            this.setStatus(ERROR)
            def result = Database.new.instantiate("sse.ErrorResult")
            result.ename = ename
            result.evalue = evalue
            result.traceback = errorData
            this.setResult(result)
            this.save()
        }

    }

    static class CodeParagraph extends BaseParagraph {
        protected CodeParagraph(Map<String, Object> entity) {
            super(entity)
        }

        @Override
        protected void copyRequestToDb() {
            Database db = Database.new
            if (this.databaseEntity.body?._type_ == null ||
                    this.databaseEntity.body._type_ != this.originalEntity.body._type_) {
                this.databaseEntity.body = db.instantiate("sse.CodeBody")
            }
            this.databaseEntity.body.interpreter = makeCodeBodyEnum("interpreter",
                    (String) this.originalEntity.body.interpreter)
            this.databaseEntity.body.text = this.originalEntity.body.text
        }

        @Override
        String getInterpreter() {
            return this.originalEntity.body.interpreter
        }

        @Override
        String getText() {
            return this.originalEntity.body.text.replace("\r", "")
        }
    }

    static class LinkParagraph extends BaseParagraph {
        private final Map<String, Object> linkedParagraph

        protected LinkParagraph(Map<String, Object> entity) {
            super(entity)
            def paragraphs = Database.new.select(
                    "select p from sse.AbstractNotebook a join a.paragraphs p where a.e_id = :e_id and p.name = :pname",
                    [pname: this.originalEntity.body.paragraphName, e_id: this.originalEntity.body.linkNotebook.e_id]
            )
            if (paragraphs.size() != 1) {
                throw new IllegalArgumentException("Cannot find linked paragraph for entity " + entity)
            }
            this.linkedParagraph = paragraphs.get(0)
        }

        @Override
        protected void copyRequestToDb() {
            Database db = Database.new
            if (this.databaseEntity.body?._type_ == null ||
                    this.databaseEntity.body._type_ != this.originalEntity.body._type_) {
                this.databaseEntity.body = db.instantiate("sse.LinkBody")
            }
            this.databaseEntity.body.linkNotebook = db.get(this.originalEntity.body.linkNotebook)
            this.databaseEntity.body.paragraphName = this.originalEntity.body.paragraphName
        }

        @Override
        String getInterpreter() {
            Objects.requireNonNull(this.linkedParagraph)
            return this.linkedParagraph.body.interpreter
        }

        @Override
        String getText() {
            Objects.requireNonNull(this.linkedParagraph)
            return this.linkedParagraph.body.text.replace("\r", "")
        }
    }

}
