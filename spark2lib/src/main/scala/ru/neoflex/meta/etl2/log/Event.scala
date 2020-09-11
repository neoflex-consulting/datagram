package ru.neoflex.meta.etl2.log

import java.text.SimpleDateFormat
import java.util.Date
import ru.neoflex.meta.etl2.log.SeverityLevel.SeverityLevel


object SeverityLevel extends Enumeration {
  type SeverityLevel = Value
  val TRACE, DEBUG, INFO, WARN, ERROR = Value
}

object Implicits {
  implicit class CaseClassToString(c: AnyRef) {
    def toStringWithFields: String = {
      val fields = (Map[String, Any]() /: c.getClass.getDeclaredFields) { (a, f) =>
        f.setAccessible(true)
        a + (f.getName -> f.get(c))
      }
      
      s"{${fields.map{case (k, v) => "\"" + k + "\": \"" + v + "\""}.mkString(", ")}}"
      
    }
  }
}

trait Event {

  def toJson: String = {
    import Implicits._
    this.toStringWithFields
  }
}

case class LogEvent(applicationId: String, applicationName: String, eventTimestamp: Date, eventType: String, severity: String, message: String, info: Option[Any]) extends Event

object LogEvent {
  def apply(applicationId: String, applicationName: String, eventTimestamp: Date, eventType: String, severity: SeverityLevel, message: String, info: Option[Any]) =
    new LogEvent(applicationId, applicationName, eventTimestamp, eventType, severity.toString, message, info)
}
