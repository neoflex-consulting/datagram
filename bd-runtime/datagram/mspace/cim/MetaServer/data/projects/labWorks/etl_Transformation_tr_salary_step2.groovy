import ru.neoflex.meta.utils.Context
def session = Context.current.txSession
def entity = session.createQuery("from etl.Transformation where name = :name").setParameter("name", "tr_salary_step2").uniqueResult() 
entity.transformationSteps.find {it.name == "excessSumAndCount"}.outputPort.fields.find {it.name == "maxRate"}.sourceFields.clear()
entity.userDefinedFunctions.clear()
entity.mavenDependencies.clear()
entity.project = session.createQuery("from etl.Project where name = :name").setParameter("name", "labWorks").uniqueResult()
entity.targets.find {it.name == "tableUpSalary"}.context = null
entity.targets.find {it.name == "tableUpSalaryConflict"}.context = null
entity.sources.find {it.name == "person"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "person").uniqueResult()
entity.sources.find {it.name == "hiveSalary"}.context = null
Context.current.commit()
