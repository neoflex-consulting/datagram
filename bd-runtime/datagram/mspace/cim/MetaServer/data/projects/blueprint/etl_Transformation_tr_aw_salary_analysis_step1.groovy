import ru.neoflex.meta.utils.Context
def session = Context.current.txSession
def entity = session.createQuery("from etl.Transformation where name = :name").setParameter("name", "tr_aw_salary_analysis_step1").uniqueResult() 
entity.userDefinedFunctions.clear()
entity.mavenDependencies.clear()
entity.project = session.createQuery("from etl.Project where name = :name").setParameter("name", "blueprint").uniqueResult()
entity.targets.find {it.name == "in_HDFS_1"}.context = null
entity.targets.find {it.name == "in_HDFS_2"}.context = null
entity.sources.find {it.name == "EmployeePayHistory_2_2"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "awHR").uniqueResult()
entity.sources.find {it.name == "EmployeePayHistory_1_2"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "awHR").uniqueResult()
entity.sources.find {it.name == "EmployeePayHistory_1"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "awHR").uniqueResult()
entity.sources.find {it.name == "EmployeePayHistory_2"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "awHR").uniqueResult()
Context.current.commit()
