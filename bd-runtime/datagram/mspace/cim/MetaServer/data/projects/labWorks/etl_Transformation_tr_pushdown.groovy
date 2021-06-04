import ru.neoflex.meta.utils.Context
def session = Context.current.txSession
def entity = session.createQuery("from etl.Transformation where name = :name").setParameter("name", "tr_pushdown").uniqueResult() 
entity.userDefinedFunctions.clear()
entity.mavenDependencies.clear()
entity.project = session.createQuery("from etl.Project where name = :name").setParameter("name", "labWorks").uniqueResult()
entity.targets.find {it.name == "histogram"}.context = null
entity.sources.find {it.name == "humanresources"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "humanresources").uniqueResult()
Context.current.commit()
