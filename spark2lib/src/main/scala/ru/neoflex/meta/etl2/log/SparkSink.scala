package ru.neoflex.meta.etl2.log

import org.slf4j.LoggerFactory

class SparkSink extends LogSink {
  val logger = LoggerFactory.getLogger(getClass)
  override def logMessage(msg: String): Unit = {
    logger.info(msg)
  }
}
