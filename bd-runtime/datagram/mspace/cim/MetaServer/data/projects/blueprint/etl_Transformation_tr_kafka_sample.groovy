import ru.neoflex.meta.utils.Context
def session = Context.current.txSession
def entity = session.createQuery("from etl.Transformation where name = :name").setParameter("name", "tr_kafka_sample").uniqueResult() 
entity.userDefinedFunctions.clear()
entity.mavenDependencies.clear()
entity.project = session.createQuery("from etl.Project where name = :name").setParameter("name", "blueprint").uniqueResult()
entity.targets.find {it.name == "Local_1"}.context = null
entity.sources.find {it.name == "Kafka_0"}.context = null
entity.sources.find {it.name == "Kafka_0"}.valueScheme = null
Context.current.commit()
