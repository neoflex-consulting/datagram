import ru.neoflex.meta.utils.Context
def session = Context.current.txSession
def entity = session.createQuery("from etl.Transformation where name = :name").setParameter("name", "tr_salary_step1").uniqueResult() 
entity.userDefinedFunctions.clear()
entity.mavenDependencies.clear()
entity.project = session.createQuery("from etl.Project where name = :name").setParameter("name", "labWorks").uniqueResult()
entity.targets.find {it.name == "lastYear"}.context = null
entity.targets.find {it.name == "lastYearSalary"}.context = null
entity.sources.find {it.name == "humanresources"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "humanresources").uniqueResult()
entity.sources.find {it.name == "person"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "person").uniqueResult()
Context.current.commit()
