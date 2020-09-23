import ru.neoflex.meta.utils.Context
def session = Context.current.txSession
def entity = session.createQuery("from etl.Transformation where name = :name").setParameter("name", "tr_uc_trp2dds").uniqueResult() 
entity.userDefinedFunctions.clear()
entity.mavenDependencies.clear()
entity.project = session.createQuery("from etl.Project where name = :name").setParameter("name", "unified_customers").uniqueResult()
entity.targets.find {it.name == "Table_27"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "customers").uniqueResult()
entity.targets.find {it.name == "Table_11"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "customers").uniqueResult()
entity.targets.find {it.name == "Table_10"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "customers").uniqueResult()
entity.targets.find {it.name == "Table_1"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "customers").uniqueResult()
entity.sources.find {it.name == "SQL_17"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "customers").uniqueResult()
entity.sources.find {it.name == "DIM_CLIENT"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "customers").uniqueResult()
entity.sources.find {it.name == "TRP_S04_DIM_CLIENT"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "customers").uniqueResult()
Context.current.commit()
