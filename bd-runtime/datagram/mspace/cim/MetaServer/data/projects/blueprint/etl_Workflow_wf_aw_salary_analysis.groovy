import ru.neoflex.meta.utils.Context
def session = Context.current.txSession
def entity = session.createQuery("from etl.Workflow where name = :name").setParameter("name", "wf_aw_salary_analysis").uniqueResult() 
entity.project = session.createQuery("from etl.Project where name = :name").setParameter("name", "blueprint").uniqueResult()
entity.nodes.find {it.name == "Transformation_5"}.transformation = session.createQuery("from etl.Transformation where name = :name").setParameter("name", "tr_aw_salary_analysis_step3").uniqueResult()
entity.nodes.find {it.name == "Transformation_4"}.transformation = session.createQuery("from etl.Transformation where name = :name").setParameter("name", "tr_aw_salary_analysis_step2").uniqueResult()
entity.nodes.find {it.name == "Create_Salary_Table"}.transformation = session.createQuery("from etl.Transformation where name = :name").setParameter("name", "tr_aw_salary_analysis_step1").uniqueResult()
Context.current.commit()
