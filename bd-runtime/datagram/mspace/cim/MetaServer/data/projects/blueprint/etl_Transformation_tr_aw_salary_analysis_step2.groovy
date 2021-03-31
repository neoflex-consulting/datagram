import ru.neoflex.meta.utils.Context
def session = Context.current.txSession
def entity = session.createQuery("from etl.Transformation where name = :name").setParameter("name", "tr_aw_salary_analysis_step2").uniqueResult() 
entity.userDefinedFunctions.clear()
entity.mavenDependencies.clear()
entity.project = session.createQuery("from etl.Project where name = :name").setParameter("name", "blueprint").uniqueResult()
entity.targets.find {it.name == "Local_19"}.context = null
entity.sources.find {it.name == "Hive_1"}.context = null
entity.sources.find {it.name == "Hive_2"}.context = null
Context.current.commit()
