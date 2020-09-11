package ru.neoflex.meta.etl2

import java.sql._
import java.util

import org.apache.spark.SparkContext
import org.apache.spark.sql.SQLContext
import ru.neoflex.meta.etl2.log.EtlJobLogging

import scala.collection.JavaConversions._
import scala.collection.immutable.HashMap
import scala.util.{Failure, Success, Try}

/**
 * Created by kostas on 07.09.2015.
 */
trait TableTarget { this: ETLJobBase with EtlJobLogging => //extends ETLJobBase with Serializable with scala.Serializable{

  def executeUpdate(context: JdbcETLContext, sqlText: String): Unit = {
    val cn = context.getConnection
    try {
      cn.setAutoCommit(false)
      logger.logInfo(s"executeUpdate query: ${sqlText}")
      val stmt = cn.prepareCall(sqlText)
      try {
        try {
          stmt.executeUpdate()
          cn.commit()
        }
        catch {
          case e: Exception => {
            cn.rollback()
            def message = s"executeUpdate of query '${sqlText}' has been failed with error message: ${e.getMessage}"
            logger.logError(message)
            throw e
          }
        }
      }
      finally {
        stmt.close()
      }
    } finally {
      cn.close()
    }
  }

  def processSlice[T](cn :Connection, stmt: CallableStatement, rowList: List[T],
                   rowFunction:(T, CallableStatement) => Unit,
                   successNotificationFunction:(Int) => Unit,
                   failNotificationFunction:(String, T) => Unit):Unit = {
    def executeBatch(cn: Connection, stmt: CallableStatement): Try[Int] = 
      Try {
        val results = stmt.executeBatch()
        cn.commit()
        results.length
      } recoverWith { case e =>
          stmt.clearBatch()
          cn.rollback()
          Failure(e)
    }
    def addBatch(row: T, stmt: CallableStatement) {
      rowFunction(row, stmt)
      stmt.addBatch()
    }
        
    rowList.foreach(addBatch(_, stmt))
    val result = executeBatch(cn, stmt) recover { case e =>
        rowList.map(row => {
          addBatch(row, stmt)
          executeBatch(cn, stmt) match {
            case Success(n) => n
            case Failure(e: BatchUpdateException) => 
              failNotificationFunction("Applying row to table target has been failed." + e.iterator.mkString("|"), row)
              0
            case Failure(e) => 0
          }
        }).reduceLeft(_ + _)
    }
    successNotificationFunction(result.get)
  }


  def notifyStart(stepName: String,rddName: String, jobStartTime:Long): Unit = {
    var resMap: Map[String, Any] = new HashMap[String, Any]
    val stepId: String = getApplicationName + stepName + "Start" + jobStartTime
    resMap = resMap + ("emitterType" -> "STATISTIC")
    resMap = resMap + ("emitterSubType" -> "DRIVER")
    resMap = resMap + ("emitterId" -> ("Target step: " + stepName))
    val timestamp = new util.Date
    resMap = resMap + ("timestamp" -> timestampFormat.format(timestamp))
    resMap = resMap + ("id" -> stepId)
    resMap = resMap + ("parentId" -> (getApplicationName + jobStartTime))
    resMap = resMap + ("statisticName" -> ("Target step: " + stepName))
    resMap = resMap + ("eventType" -> "START")
    resMap = resMap + ("message" -> ("Writing to target: " + stepName))
    resMap = resMap + ("rddName" -> rddName)

    sendNotification(resMap)
  }


  def notifyFinish(stepName: String,rddName: String, jobStartTime:Long): Unit = {
    var resMap: Map[String, Any] = new HashMap[String, Any]

    val stepId: String = getApplicationName + stepName + "Start" + jobStartTime
    resMap = resMap + ("emitterType" -> "STATISTIC")
    resMap = resMap + ("emitterSubType" -> "DRIVER")
    resMap = resMap + ("emitterId" -> ("Target step: " + stepName))
    val timestamp = new util.Date
    resMap = resMap + ("timestamp" -> timestampFormat.format(timestamp))
    resMap = resMap + ("id" -> stepId)
    resMap = resMap + ("parentId" -> (getApplicationName + jobStartTime))
    resMap = resMap + ("statisticName" -> ("Target step: " + stepName))
    resMap = resMap + ("eventType" -> "FINISH")
    resMap = resMap + ("message" -> ("Writing to target: " + stepName))
    resMap = resMap + ("rddName" -> rddName)

    sendNotification(resMap)
  }

  def notifyExecUpdate(stepName: String,rddName: String, jobStartTime:Long, successRowCount:Int): Unit = {
    var resMap: Map[String, Any] = new HashMap[String, Any]
    resMap = resMap + ("emitterType" -> "STATISTIC")
    resMap = resMap + ("emitterSubType" -> "WORKER")
    resMap = resMap + ("emitterId" -> ("Target step: " + stepName))
    val timestamp = new util.Date
    resMap = resMap + ("timestamp" -> timestampFormat.format(timestamp))
    resMap = resMap + ("id" -> ("tgt" +stepName + System.currentTimeMillis().toString))
    resMap = resMap + ("parentId" -> (getApplicationName + jobStartTime))
    resMap = resMap + ("statisticName" -> ("Target step: " + stepName))
    resMap = resMap + ("eventType" -> "EXEC_UPDATE")
    resMap = resMap + ("message" -> ("Slice processed. Rows cnt = " + successRowCount))
    resMap = resMap + ("tuplesProcessed" -> successRowCount)
    resMap = resMap + ("rddName" -> rddName)
    sendNotification(resMap)
  }


  def notifyException[T](stepName: String,rddName: String, jobStartTime:Long, errorMessage:String, failedRow:T): Unit = {
    var resMap: Map[String, Any] = new HashMap[String, Any]
    resMap = resMap + ("emitterType" -> "STATISTIC")
    resMap = resMap + ("emitterSubType" -> "WORKER")
    resMap = resMap + ("emitterId" -> ("Target step: " + stepName))
    val timestamp = new util.Date
    resMap = resMap + ("timestamp" -> timestampFormat.format(timestamp))
    resMap = resMap + ("id" -> ("tgt" +stepName + System.currentTimeMillis().toString))
    resMap = resMap + ("parentId" -> (getApplicationName + jobStartTime))
    resMap = resMap + ("statisticName" -> ("Target step: " + stepName))
    resMap = resMap + ("eventType" -> "EXCEPTION")
    resMap = resMap + ("message" -> (errorMessage /*+ traceRow(failedRow)*/))
    resMap = resMap + ("tuplesFailed" -> 1)
    resMap = resMap + ("rddName" -> rddName)

    sendNotification(resMap)
  }

  def saveRejects(sc: SparkContext, sqlCtx: SQLContext, schema: org.apache.spark.sql.types.StructType, fileName: String, rejects: util.List[util.Map[String, AnyRef]]): Unit = {
    val fs = org.apache.hadoop.fs.FileSystem.get(sc.hadoopConfiguration)
    if (fs.exists(new org.apache.hadoop.fs.Path(fileName))) {
      fs.delete(new org.apache.hadoop.fs.Path(fileName), true)
    }
    sqlCtx.createDataFrame(sc.parallelize(rejects.map(m=>{
      org.apache.spark.sql.Row.fromSeq(schema.fieldNames.map(m.get(_)))
    })), schema).write.parquet(fileName)

  }

}
