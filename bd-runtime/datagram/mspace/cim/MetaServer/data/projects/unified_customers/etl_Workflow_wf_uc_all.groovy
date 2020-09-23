import ru.neoflex.meta.utils.Context
def session = Context.current.txSession
def entity = session.createQuery("from etl.Workflow where name = :name").setParameter("name", "wf_uc_all").uniqueResult() 
entity.project = session.createQuery("from etl.Project where name = :name").setParameter("name", "unified_customers").uniqueResult()
entity.nodes.find {it.name == "tr_uc_trp2dds"}.transformation = session.createQuery("from etl.Transformation where name = :name").setParameter("name", "tr_uc_trp2dds").uniqueResult()
entity.nodes.find {it.name == "tr_uc_stg2trp"}.transformation = session.createQuery("from etl.Transformation where name = :name").setParameter("name", "tr_uc_stg2trp").uniqueResult()
entity.nodes.find {it.name == "tr_uc_csv2stg"}.transformation = session.createQuery("from etl.Transformation where name = :name").setParameter("name", "tr_uc_csv2stg").uniqueResult()
Context.current.commit()
