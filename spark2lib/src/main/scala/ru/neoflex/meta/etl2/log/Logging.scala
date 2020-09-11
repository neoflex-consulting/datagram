package ru.neoflex.meta.etl2.log

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, Row}
import ru.neoflex.meta.etl2.ETLJobBase
import ru.neoflex.meta.etl2.log.SeverityLevel.{SeverityLevel, _}

trait Logger extends Serializable {
  def logDataFrame(df: DataFrame): Unit = {
    val msg = df.schema.treeString
    logMessage(msg, INFO)
  }

  def logRow(row: Row): Unit = {} // FIXME
  def logRDD[T](rdd: RDD[T]): Unit = {} // FIXME

  def logTrace(msg: => String): Unit = logMessage(msg, TRACE)
  def logDebug(msg: => String): Unit = logMessage(msg, DEBUG)
  def logInfo(msg: => String): Unit = logMessage(msg, INFO)
  def logInfo(msg: => String, info: Any): Unit = logMessage(msg, INFO, Some(info))
  def logWarn(msg: => String): Unit = logMessage(msg, WARN)
  def logError(msg: => String): Unit = logMessage(msg, ERROR)
  def logError(msg: => String, e: Throwable): Unit = logMessage(msg, ERROR) //FIXME

  def logSparkEvent(event: Map[String, Any]): Unit = {
    logSparkEvent(event("eventType").toString, event)
  }

  protected def logMessage(msg: => String, severityLevel: SeverityLevel, info: Option[Any] = None): Unit
  protected def logSparkEvent(eventType: String, event: Map[String, Any]): Unit

}

class EtlJobLogger(etlJob: ETLJobBase) extends Logger {
  val logDir = s"${etlJob._defaultFS}${etlJob._workflowHome}"
  private val sparkSink = new SparkSink

  private val logSink = new CompositeSink(sparkSink)

  final protected override def logMessage(msg: => String, severityLevel: SeverityLevel, info: Option[Any]): Unit = {
    val evt = LogEvent(etlJob._applicationId, etlJob.getApplicationName, new java.util.Date(), "USER", severityLevel, msg, info)
    logSink.logMessage(evt.toJson)
  }

  final protected override def logSparkEvent(eventType: String, event: Map[String, Any]): Unit = {
    val evt = LogEvent(etlJob._applicationId, etlJob.getApplicationName, new java.util.Date(), eventType, INFO, "SparkEvent", Some(event))
    logSink.logMessage(evt.toJson)
  }

}

trait EtlJobLogging { this: ETLJobBase =>
  lazy val logger = new EtlJobLogger(this)
}

object DummyLogger extends Logger {
  final protected override def logMessage(msg: => String, severityLevel: SeverityLevel, info: Option[Any]): Unit = ()
  final protected override def logSparkEvent(eventType: String, event: Map[String, Any]): Unit = ()
}

