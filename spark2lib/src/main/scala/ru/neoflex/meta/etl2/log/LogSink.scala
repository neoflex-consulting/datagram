package ru.neoflex.meta.etl2.log

trait LogSink extends Serializable {
  def logMessage(msg: String): Unit
}

class NoOpSink extends LogSink {
  override def logMessage(msg: String): Unit = {}
}

class CompositeSink(sinks: LogSink*) extends LogSink {

  override def logMessage(msg: String): Unit = {
    sinks.foreach(_.logMessage(msg))
  }
}
