import ru.neoflex.meta.utils.Context
def session = Context.current.txSession
def entity = session.createQuery("from etl.Project where name = :name").setParameter("name", "labWorks").uniqueResult() 
entity.parentProject = null
Context.current.commit()
