import ru.neoflex.meta.utils.Context
def session = Context.current.txSession
def entity = session.createQuery("from etl.Transformation where name = :name").setParameter("name", "tr_load_datagram_transformations").uniqueResult() 
entity.userDefinedFunctions.clear()
entity.mavenDependencies.clear()
entity.project = session.createQuery("from etl.Project where name = :name").setParameter("name", "blueprint").uniqueResult()
entity.targets.find {it.name == "Local_9"}.context = null
entity.sources.find {it.name == "SQL_7"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "teneo").uniqueResult()
entity.sources.find {it.name == "SQL_1"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "teneo").uniqueResult()
entity.sources.find {it.name == "SQL_0"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "teneo").uniqueResult()
Context.current.commit()
