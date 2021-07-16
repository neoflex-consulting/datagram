import ru.neoflex.meta.utils.Context
def session = Context.current.txSession
def entity = session.createQuery("from etl.Transformation where name = :name").setParameter("name", "tr_yandex_cloud_S3").uniqueResult() 
entity.userDefinedFunctions.clear()
entity.mavenDependencies.clear()
entity.project = session.createQuery("from etl.Project where name = :name").setParameter("name", "blueprint").uniqueResult()
entity.targets.find {it.name == "lastYearORC"}.context = null
entity.targets.find {it.name == "lastYearCSV"}.context = null
entity.sources.find {it.name == "employeepayhistory"}.context = null
entity.sources.find {it.name == "CSV_3"}.context = null
Context.current.commit()
