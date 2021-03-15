import ru.neoflex.meta.utils.Context
def session = Context.current.txSession
def entity = session.createQuery("from etl.Transformation where name = :name").setParameter("name", "tr_adventureWorks").uniqueResult() 
entity.transformationSteps.find {it.name == "Projection_5"}.outputPort.fields.find {it.name == "Option0"}.sourceFields.clear()
entity.userDefinedFunctions.clear()
entity.mavenDependencies.clear()
entity.project = session.createQuery("from etl.Project where name = :name").setParameter("name", "blueprint").uniqueResult()
entity.targets.find {it.name == "CSV_11"}.context = null
entity.sources.find {it.name == "EmployeePayHistory_1"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "awHR").uniqueResult()
entity.sources.find {it.name == "Table_4"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "awHR").uniqueResult()
entity.sources.find {it.name == "SQL_5"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "awHR").uniqueResult()
Context.current.commit()
