import ru.neoflex.meta.utils.Context
def session = Context.current.txSession
def entity = session.createQuery("from rt.SoftwareSystem where name = :name").setParameter("name", "awSales").uniqueResult() 
entity.project = session.createQuery("from etl.Project where name = :name").setParameter("name", "blueprint").uniqueResult()
entity.scheme = session.createQuery("from rel.Scheme where name = :name").setParameter("name", "sales_at_awSales").uniqueResult()
entity.defaultDeployment = session.createQuery("from rt.Deployment where name = :name").setParameter("name", "awSales").uniqueResult()
Context.current.commit()
