import ru.neoflex.meta.utils.Context
def session = Context.current.txSession
def entity = session.createQuery("from etl.Workflow where name = :name").setParameter("name", "wf_load_datagram_transformations").uniqueResult() 
entity.project = session.createQuery("from etl.Project where name = :name").setParameter("name", "blueprint").uniqueResult()
entity.nodes.find {it.name == "Transformation_3"}.transformation = session.createQuery("from etl.Transformation where name = :name").setParameter("name", "tr_load_datagram_transformations").uniqueResult()
Context.current.commit()
