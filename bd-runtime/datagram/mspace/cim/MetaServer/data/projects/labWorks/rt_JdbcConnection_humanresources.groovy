import ru.neoflex.meta.utils.Context
def session = Context.current.txSession
def entity = session.createQuery("from rt.JdbcConnection where name = :name").setParameter("name", "humanresources").uniqueResult() 
entity.project = session.createQuery("from etl.Project where name = :name").setParameter("name", "labWorks").uniqueResult()
Context.current.commit()
