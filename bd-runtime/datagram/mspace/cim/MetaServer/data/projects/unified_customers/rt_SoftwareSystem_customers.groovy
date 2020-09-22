import ru.neoflex.meta.utils.Context
def session = Context.current.txSession
def entity = session.createQuery("from rt.SoftwareSystem where name = :name").setParameter("name", "customers").uniqueResult() 
entity.project = session.createQuery("from etl.Project where name = :name").setParameter("name", "unified_customers").uniqueResult()
entity.scheme = session.createQuery("from rel.Scheme where name = :name").setParameter("name", "ML_at_customers").uniqueResult()
entity.defaultDeployment = session.createQuery("from rt.Deployment where name = :name").setParameter("name", "customers").uniqueResult()
Context.current.commit()
