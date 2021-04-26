import ru.neoflex.meta.utils.Context
def session = Context.current.txSession
def entity = session.createQuery("from etl.Transformation where name = :name").setParameter("name", "tr_aw_interpretation_needed").uniqueResult() 
entity.userDefinedFunctions.clear()
entity.mavenDependencies.clear()
entity.project = session.createQuery("from etl.Project where name = :name").setParameter("name", "blueprint").uniqueResult()
entity.targets.find {it.name == "Table_13"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "dwh").uniqueResult()
entity.sources.find {it.name == "productdescription"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "awProduction").uniqueResult()
entity.sources.find {it.name == "productmodelproductdescriptionculture"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "awProduction").uniqueResult()
entity.sources.find {it.name == "productmodel"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "awProduction").uniqueResult()
entity.sources.find {it.name == "culture"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "awProduction").uniqueResult()
Context.current.commit()
