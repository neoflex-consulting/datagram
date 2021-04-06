import ru.neoflex.meta.utils.Context
def session = Context.current.txSession
def entity = session.createQuery("from etl.Workflow where name = :name").setParameter("name", "wf_salary_analysis").uniqueResult() 
entity.project = session.createQuery("from etl.Project where name = :name").setParameter("name", "labWorks").uniqueResult()
entity.nodes.find {it.name == "step1"}.transformation = session.createQuery("from etl.Transformation where name = :name").setParameter("name", "tr_salary_step1").uniqueResult()
entity.nodes.find {it.name == "step2"}.transformation = session.createQuery("from etl.Transformation where name = :name").setParameter("name", "tr_salary_step2").uniqueResult()
Context.current.commit()
