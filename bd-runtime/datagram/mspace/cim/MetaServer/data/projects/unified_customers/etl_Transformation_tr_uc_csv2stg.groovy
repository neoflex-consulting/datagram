import ru.neoflex.meta.utils.Context
def session = Context.current.txSession
def entity = session.createQuery("from etl.Transformation where name = :name").setParameter("name", "tr_uc_csv2stg").uniqueResult() 
entity.transformationSteps.find {it.name == "Projection_2"}.outputPort.fields.find {it.name == "PROCESSED_DTTM"}.sourceFields.clear()
entity.transformationSteps.find {it.name == "Projection_2"}.outputPort.fields.find {it.name == "AUTO_FLAG"}.sourceFields.clear()
entity.transformationSteps.find {it.name == "Projection_2"}.outputPort.fields.find {it.name == "AUTO_MODE"}.sourceFields.clear()
entity.transformationSteps.find {it.name == "Projection_2"}.outputPort.fields.find {it.name == "LOAD_ID"}.sourceFields.clear()
entity.userDefinedFunctions.clear()
entity.transformationSteps.find {it.name == "Projection_2"}.outputPort.fields.find {it.name == "WORKFLOW_RUN_ID"}.sourceFields.clear()
entity.transformationSteps.find {it.name == "Projection_2"}.outputPort.fields.find {it.name == "EMPTY_STRING"}.sourceFields.clear()
entity.mavenDependencies.clear()
entity.project = session.createQuery("from etl.Project where name = :name").setParameter("name", "unified_customers").uniqueResult()
entity.targets.find {it.name == "Table_1"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "customers").uniqueResult()
entity.sources.find {it.name == "CSV_0"}.context = null
Context.current.commit()
