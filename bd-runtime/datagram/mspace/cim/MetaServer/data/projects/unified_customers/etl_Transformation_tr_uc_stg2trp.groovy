import ru.neoflex.meta.utils.Context
def session = Context.current.txSession
def entity = session.createQuery("from etl.Transformation where name = :name").setParameter("name", "tr_uc_stg2trp").uniqueResult() 
entity.userDefinedFunctions.clear()
entity.mavenDependencies.clear()
entity.project = session.createQuery("from etl.Project where name = :name").setParameter("name", "unified_customers").uniqueResult()
entity.targets.find {it.name == "Table_15"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "customers").uniqueResult()
entity.sources.find {it.name == "IDM_MNS_BUSINESS_CATEGORY"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "customers").uniqueResult()
entity.sources.find {it.name == "IDM_MNS_COUNTRY"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "customers").uniqueResult()
entity.sources.find {it.name == "IDM_PKB_CLIENT_GROUP"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "customers").uniqueResult()
entity.sources.find {it.name == "STG_S04_UNIF_CLIENT"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "customers").uniqueResult()
entity.sources.find {it.name == "IDM_UNIF_CLIENT"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "customers").uniqueResult()
entity.sources.find {it.name == "IDM_NSI_VTB_GROUP_MEMBER"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "customers").uniqueResult()
entity.sources.find {it.name == "IDM_MNS_INDUSTRY"}.context = session.createQuery("from etl.JdbcContext where name = :name").setParameter("name", "customers").uniqueResult()
Context.current.commit()
