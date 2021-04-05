import ru.neoflex.meta.utils.Context
def session = Context.current.txSession
def entity = session.createQuery("from etl.Transformation where name = :name").setParameter("name", "tr_aw_salary_analysis_step3").uniqueResult() 
entity.userDefinedFunctions.clear()
entity.mavenDependencies.clear()
entity.project = session.createQuery("from etl.Project where name = :name").setParameter("name", "blueprint").uniqueResult()
entity.targets.find {it.name == "CSV_7"}.context = null
entity.sources.find {it.name == "Hive_1"}.context = null
entity.sources.find {it.name == "SQL_5"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "awPerson").uniqueResult()
Context.current.commit()
