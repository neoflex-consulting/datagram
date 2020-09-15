import ru.neoflex.meta.utils.Context
def session = Context.current.txSession
def entity = session.createQuery("from rt.LivyServer where name = :name").setParameter("name", "bd-livy").uniqueResult() 
entity.project = session.createQuery("from etl.Project where name = :name").setParameter("name", "blueprint").uniqueResult()
Context.current.commit()
