import ru.neoflex.meta.utils.Context
def session = Context.current.txSession
def entity = session.createQuery("from etl.CoJob where name = :name").setParameter("name", "co_load_datagram_transformations").uniqueResult() 
entity.project = session.createQuery("from etl.Project where name = :name").setParameter("name", "blueprint").uniqueResult()
entity.action.workflow = session.createQuery("from etl.Workflow where name = :name").setParameter("name", "wf_load_datagram_transformations").uniqueResult()
Context.current.commit()
