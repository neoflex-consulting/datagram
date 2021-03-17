import ru.neoflex.meta.utils.Context
def session = Context.current.txSession
def entity = session.createQuery("from etl.Transformation where name = :name").setParameter("name", "tr_aw_long_time_no_sale").uniqueResult() 
entity.userDefinedFunctions.clear()
entity.mavenDependencies.clear()
entity.project = session.createQuery("from etl.Project where name = :name").setParameter("name", "blueprint").uniqueResult()
entity.targets.find {it.name == "Local_ORC"}.context = null
entity.targets.find {it.name == "Local_PARQUET"}.context = null
entity.sources.find {it.name == "SalesOrderHeader"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "awSales").uniqueResult()
entity.sources.find {it.name == "Customer"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "awSales").uniqueResult()
entity.sources.find {it.name == "Store"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "awSales").uniqueResult()
Context.current.commit()
