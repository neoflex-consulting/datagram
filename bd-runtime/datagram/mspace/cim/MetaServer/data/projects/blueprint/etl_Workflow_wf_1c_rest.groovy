import ru.neoflex.meta.utils.Context
def session = Context.current.txSession
def entity = session.createQuery("from etl.Workflow where name = :name").setParameter("name", "wf_1c_rest").uniqueResult() 
entity.project = session.createQuery("from etl.Project where name = :name").setParameter("name", "blueprint").uniqueResult()
entity.nodes.find {it.name == "Transformation_2"}.transformation = session.createQuery("from etl.Transformation where name = :name").setParameter("name", "tr_1c_crmcomtact_list_load").uniqueResult()
Context.current.commit()
