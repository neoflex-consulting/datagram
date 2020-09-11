package ru.neoflex.meta.etl2

import java.io._
import java.nio.charset.Charset
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Date

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import org.apache.commons.codec.binary.Base64
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FSDataOutputStream, FileSystem, Path}
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.executor.TaskMetrics
import org.apache.spark.scheduler._
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.RDDInfo
import org.apache.spark.{SparkContext, SparkStageInfo}
import ru.neoflex.meta.etl2.log.EtlJobLogging

import scala.collection.immutable.HashMap
import scala.collection.mutable

trait ETLJobBase extends TableTarget with EtlJobLogging with Serializable {
  implicit lazy val formats = org.json4s.DefaultFormats
  val NTF_CTX_NAME = "NTF_CTX_NAME"
  val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
  val timestampFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  val timeFormat = new SimpleDateFormat("HH:mm:ss.SSS")
  var _master: String = ""
  var _workflowId: String = ""
  var _rootWorkflowId: String = ""
  var _workflowHome: String = ""
  var _applicationId: String = ""
  var _applicationHome: String = ""
  var _defaultFS: String = ""
  var _slideSize: Int = 400
  var _partitionNum: Int = 4
  var _fetchSize: Int = 100000
  var _rejectSize: Int = 100
  var _debug: Boolean = false
  val _contexts: mutable.HashMap[String, ETLContext] = new mutable.HashMap[String, ETLContext]()
  private val _jobParameters: mutable.HashMap[String, AnyRef] = new mutable.HashMap[String, AnyRef]()
  private var _jobParametersBc: Broadcast[mutable.HashMap[String, AnyRef]] = null
  @transient lazy val _fs:FileSystem = initLogFileSystem()
  @transient var sc : SparkContext = null

  def jobParameters: mutable.HashMap[String, AnyRef] = if (_jobParametersBc == null) { _jobParameters } else { _jobParametersBc.value }

  private def decodeContextPass() {
    val fileNamePassKey = s"${_defaultFS}/${_workflowHome}/skey.bin"
    val path = new org.apache.hadoop.fs.Path(fileNamePassKey)
    if (_fs.exists(path)) {
      val stream = _fs.open(new org.apache.hadoop.fs.Path(fileNamePassKey))
      val key = new Array[Byte](_fs.getContentSummary(path).getLength().toInt)
      try {
        stream.readFully(0, key)
      } finally {
        stream.close()
      }

      for ((k,v) <- _contexts) {
        v.asInstanceOf[JdbcETLContext]._password = decodePass(v.asInstanceOf[JdbcETLContext]._password, key)
      }
    }
  }
  
  private def decodePass(pass: String, key: Array[Byte]): String = {
    val input = Base64.decodeBase64(pass);
    val secretKeySpec:SecretKeySpec = new SecretKeySpec(key, "AES");
    try {
        val cipher:Cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
        val output = cipher.doFinal(input);
        val result:String = new String(output, "UTF-8");
        return result;
    } catch {
      case e: Throwable =>
        logger.logError(s"------->Exception while runJob(): ${e.getMessage}", e)
        throw new RuntimeException(e)
    }
  }

  def parse(args: Seq[String]) {
    for (arg <- args) {
      val parts = Utils.unescape(arg).split("=")
      val (param, value) = (parts(0), parts.tail.mkString("="))
      _jobParameters.put(param, value)
      if (param.startsWith("JDBC_")) {
        var argSplit = param.split("[_]")
        var name = argSplit(1)
        var context: JdbcETLContext = _contexts.getOrElseUpdate(name, new JdbcETLContext(name)).asInstanceOf[JdbcETLContext]
        argSplit(2) match {
          // Защита от случая URL для Oracle в виде полного TNSNAME вида (DESCRITPION= ..) см тест
          case "URL" => context._url = parts.tail.mkString("=")
          case "SCHEMA" => context._schema = value
          case "USER" => context._user = value
          case "PASSWORD" => context._password = value
          case "DRIVER" => context._driverClassName = value
        }
      }
      else if (param.equals("CURRENT_WORKFLOW_ID")) {
        _workflowId = value
      }
      else if (param.equals("ROOT_WORKFLOW_ID")) {
        _rootWorkflowId = value
      }
      else if (param.equals("MASTER")) {
        _master = value
      }
      else if (param.equals("PARTITION_NUM")) {
        _partitionNum = Integer.parseInt(value)
      }
      else if (param.equals("SLIDE_SIZE")) {
        _slideSize = Integer.parseInt(value)
      }
      else if (param.equals("FETCH_SIZE")) {
        _fetchSize = Integer.parseInt(value)
      }
      else if (param.equals("FAIL_THRESHOLD")) {
        _rejectSize = Integer.parseInt(value)
      }
      else if (param.equals("DEBUG")) {
        _debug = java.lang.Boolean.parseBoolean(value)
      }
      else if (param.startsWith("NTF_")) {
        var argSplit = param.split("[_]")
      }
    }
  }

  def getContext(name: String): ETLContext = {
    _contexts.get(name).getOrElse(null)
  }

  private def readPersistedParameters(fs: FileSystem, spark: SparkSession): Unit = {
    val fileNameParquet = s"${_defaultFS}/${_workflowHome}/${_rootWorkflowId}.parquet"
    if (fs.exists(new org.apache.hadoop.fs.Path(fileNameParquet))) {
      val parquetDF = spark.read.parquet(fileNameParquet)
      val count = parquetDF.collect.map(row => {
        val name = row.getAs[String]("NAME")
        val value = row.getAs[String]("VALUE")
        logger.logInfo(s"""persistent parameter "$name"="$value"""")
        _jobParameters.put(name, value)
        1
      }).sum
      logger.logInfo(s"got $count parameters from $fileNameParquet")
    }
    val fileNameJSON = s"${_defaultFS}/${_workflowHome}/${_rootWorkflowId}.json"
    if (fs.exists(new org.apache.hadoop.fs.Path(fileNameJSON))) {
      val jsonDF = spark.read.json(fileNameJSON)
      val count = jsonDF.collect.map(row => {
        val name = row.getAs[String]("NAME")
        val value = row.getAs[String]("VALUE")
       logger.logInfo(s"""persistent parameter "$name"="$value"""")
        _jobParameters.put(name, value)
        1
      }).sum
      logger.logInfo(s"got $count parameters from $fileNameJSON")
    }
    val fileNameProps = s"${_defaultFS}/${_workflowHome}/${_rootWorkflowId}.properties"
    if (fs.exists(new org.apache.hadoop.fs.Path(fileNameProps))) {
      parse(readFile(fs, new org.apache.hadoop.fs.Path(fileNameProps)))
    }
  }

  def readFile(fs: FileSystem, path: Path): Seq[String] = {
    if (fs.exists(path)) {
      val stream = fs.open(path)
      try {

        val result = new collection.mutable.MutableList[String]
        val reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))

        var line: String = null
        line = reader.readLine

        while (line != null) {
          result += line
          line = reader.readLine
        }

        result.toSeq
      } finally {
        stream.close()
      }
    } else {
      Seq()
    }
  }

  def writeChildInfo(fs: FileSystem) {
    val workflowRelation = Map("root workflow" -> _jobParameters("ROOT_WORKFLOW_ID"),
      "child workflow" -> _jobParameters("CURRENT_WORKFLOW_ID"),
      "app name" -> getApplicationName,
      "appId" -> _applicationId,
      "run params" -> _jobParameters)

    implicit lazy val formats = org.json4s.DefaultFormats
    val workflowRelationString = org.json4s.jackson.Serialization.write(workflowRelation)

    val fileName = s"${_defaultFS}/${_workflowHome}/${_workflowId}/${_applicationId}.child"
    appendFile(fs, new Path(fileName), Seq(workflowRelationString))

  }

  def appendFile(fs: FileSystem, path: Path, data: Seq[String]): Unit = {
    if (!fs.exists(path)) {
      saveFile(fs, path, data)
    } else {
      val stream = fs.append(path)
      write(stream, data)
    }
  }

  def saveFile(fs: FileSystem, path: Path, data: Seq[String]): Unit = {
    val stream = fs.create(path)
    write(stream, data)
  }

  def write(stream: FSDataOutputStream, data: Seq[String]): Unit = {
    try {
      stream.write((data.mkString("\n") + "\n").getBytes(Charset.forName("UTF8")))
    } finally {
      stream.close()
    }
  }

	def initBuilder(builder: SparkSession.Builder): SparkSession.Builder = {
	    builder
	}

  def sparkMain(args: Array[String]) {
    parse(args.toSeq)
    val spark = initBuilder(SparkSession
      .builder()
      .master(_master)
      .appName(getApplicationName)
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer"))
      .getOrCreate()

    sc = spark.sparkContext

    try {
      runJob(spark)
    }
    finally {
      spark.stop()
    }
  }

  def sendNotification(msg: Map[String, Any]): Unit = {
    val resMap = msg + ("transformationName" -> getApplicationName) + ("_type_" -> "etlrt.RuntimeEvent")
    logger.logSparkEvent(resMap)
  }


  def getApplicationName: String

  def run(spark: SparkSession): Any

  def traceRow(row: scala.Array[AnyRef]):String = {
    var res: String = "Row:"
    for (key <- row) yield {
      res = res + "|" + key
    }
    res
  }

  private def initLogFileSystem():FileSystem = {
    val hadoopConfDir = System.getenv("HADOOP_CONF_DIR")
    val config = new Configuration()
    config.addResource(new Path(s"$hadoopConfDir/core-site.xml"))
    config.addResource(new Path(s"$hadoopConfDir/hdfs-site.xml"))
    val fs:FileSystem = org.apache.hadoop.fs.FileSystem.get(config)
    fs
  }

  def runJob(spark: SparkSession): Any = {
    val sc = spark.sparkContext
    val sysProp = System.getProperties
    sysProp.setProperty("oracle.jdbc.J2EE13Compliant", "true")
    sysProp.setProperty("oracle.jdbc.mapDateToTimestamp", "true")

    val home = _jobParameters.getOrElse("HOME", s"/user")
    val user = _jobParameters.getOrElse("USER", s"spark")
    _workflowHome = _jobParameters.getOrElse("WF_HOME", s"${home}/${user}").toString

    _applicationId = sc.applicationId
    _applicationHome = s"${_workflowHome}/${_applicationId}"
    val config:Configuration = sc.hadoopConfiguration
    val hadoopConfDir = System.getenv("HADOOP_CONF_DIR")
    config.addResource(new Path(s"$hadoopConfDir/core-site.xml"))
    config.addResource(new Path(s"$hadoopConfDir/hdfs-site.xml"))
    _defaultFS = config.get("fs.defaultFS")
    sc.setCheckpointDir(s"${_defaultFS}${_applicationHome}/checkpoints")


    logger.logInfo(s"Log dir: ${logger.logDir} for applicationId: ${sc.applicationId}")

    val conf = sc.hadoopConfiguration
    val fs = org.apache.hadoop.fs.FileSystem.get(conf)

    readPersistedParameters(_fs, spark)
    logger.logInfo("jobParameters", jobParameters)
    _jobParametersBc = sc.broadcast(_jobParameters)

    decodeContextPass()
    
    sc.addSparkListener(new SparkListener {
      override def onStageCompleted(event: SparkListenerStageCompleted): Unit = {
        var resMap: Map[String, Any] = new HashMap[String, Any]

        resMap = resMap + ("emitterType" -> "STATISTIC")
        resMap = resMap + ("emitterSubType" -> "DRIVER")
        resMap = resMap + ("emitterId" -> ("Stage: " +  event.stageInfo.stageId.toString))
        val timestamp = new Date
        resMap = resMap + ("timestamp" -> timestampFormat.format(timestamp))
        resMap = resMap + ("id" -> ("finish" + event.stageInfo.stageId))
        resMap = resMap + ("parentId" -> (getApplicationName + sc.startTime))
        resMap = resMap + ("workflowId" -> _workflowId)

        resMap = resMap + ("statisticName" -> ("Stage " + event.stageInfo.name))
        resMap = resMap + ("eventType" -> ("FINISH"))

        if (event.stageInfo != null && event.stageInfo.rddInfos != null) {
          val rdd = event.stageInfo.rddInfos.filterNot(p => "MapPartitionsRDD".equals(p.name)).
            reduceLeft((x,y) => if (x.id > y.id) x else y)
          if (rdd != null) resMap = resMap + ("rddName" -> rdd.asInstanceOf[RDDInfo].name)
        }

        resMap = resMap + ("message" -> ("Stage finished: " + event.stageInfo.name))

        sendNotification(resMap)
      }


      override def onStageSubmitted(event: SparkListenerStageSubmitted): Unit = {
        var resMap: Map[String, Any] = new HashMap[String, Any]

        resMap = resMap + ("emitterType" -> "STATISTIC")
        resMap = resMap + ("emitterSubType" -> "DRIVER")
        resMap = resMap + ("emitterId" ->("Stage: " +  event.stageInfo.stageId.toString))
        val timestamp = new Date
        resMap = resMap + ("timestamp" -> timestampFormat.format(timestamp))
        resMap = resMap + ("id" -> ("start" + event.stageInfo.stageId))
        resMap = resMap + ("parentId" -> (getApplicationName + sc.startTime))
        resMap = resMap + ("workflowId" -> _workflowId)
        resMap = resMap + ("statisticName" -> ("Stage " + event.stageInfo.name))
        resMap = resMap + ("eventType" -> ("START"))
        resMap = resMap + ("message" -> ("Stage submitted: " + event.stageInfo.name))

        if (event.stageInfo != null && event.stageInfo.rddInfos != null) {
          val rdd = event.stageInfo.rddInfos.filterNot(p => "MapPartitionsRDD".equals(p.name)).
            reduceLeft((x,y) => if (x.id > y.id) x else y)
          if (rdd != null) resMap = resMap + ("rddName" -> rdd.asInstanceOf[RDDInfo].name)
        }

        sendNotification(resMap)
      }

      override def onTaskStart(event: SparkListenerTaskStart): Unit = {

      }

      override def onTaskGettingResult(event: SparkListenerTaskGettingResult): Unit = {
        println("Event" + event.taskInfo.id)
      }

      override def onTaskEnd(event: SparkListenerTaskEnd): Unit = {
        var resMap: Map[String, Any] = new HashMap[String, Any]

        resMap = resMap + ("emitterType" -> "STATISTIC")
        resMap = resMap + ("emitterSubType" -> "DRIVER")
        resMap = resMap + ("emitterId" -> ("Stage: " + event.stageId))
        val timestamp = new Date
        resMap = resMap + ("timestamp" -> timestampFormat.format(timestamp))
        resMap = resMap + ("id" -> ("tsk" + event.stageId +"." +event.taskInfo.id))
        resMap = resMap + ("parentId" -> (getApplicationName + sc.startTime))
        resMap = resMap + ("workflowId" -> _workflowId)
        val stageInfo: SparkStageInfo = sc.statusTracker.getStageInfo(event.stageId).get
        resMap = resMap + ("statisticName" -> ("Stage " + stageInfo.name))

        resMap = resMap + ("eventType" -> ("EXEC_UPDATE"))
        resMap = resMap + ("tasksTotal" -> (stageInfo.numTasks()))
        resMap = resMap + ("tasksFailed" -> (stageInfo.numFailedTasks()))
        resMap = resMap + ("tasksProcessed" -> (stageInfo.numCompletedTasks()))

        val taskMetrics: TaskMetrics = event.taskMetrics
        if (taskMetrics != null) {
          if (taskMetrics.outputMetrics != null) resMap = resMap + ("recordsWritten" -> (taskMetrics.outputMetrics.recordsWritten))
          if (taskMetrics.shuffleReadMetrics != null) resMap = resMap + ("shuffleReadMetrics" -> (taskMetrics.shuffleReadMetrics.recordsRead))
          //if (taskMetrics.shuffleWriteMetrics != null) resMap = resMap + ("tuplesProcessed" -> (taskMetrics.shuffleWriteMetrics.shuffleRecordsWritten))
          resMap = resMap + ("resultSize" -> (taskMetrics.resultSize))
        }
        resMap = resMap + ("message" -> ("Stage task : "))

        sendNotification(resMap)
      }

      override def onJobStart(event: SparkListenerJobStart): Unit = {
        var resMap: Map[String, Any] = new HashMap[String, Any]

        resMap = resMap + ("emitterType" -> "EXECUTION")
        resMap = resMap + ("emitterSubType" -> "DRIVER")
        resMap = resMap + ("emitterId" -> getApplicationName)
        val timestamp = new Date(event.time)
        resMap = resMap + ("timestamp" -> timestampFormat.format(timestamp))
        resMap = resMap + ("id" -> (getApplicationName + sc.startTime))
        resMap = resMap + ("workflowId" -> _workflowId)
        resMap = resMap + ("master" -> _master)
        resMap = resMap + ("actualDate" -> dateFormat.format(new Timestamp(dateFormat.parse(_jobParameters.getOrElse("actualDate", "1970-12-31").toString).getTime)))
        resMap = resMap + ("eventType" -> ("START"))
        resMap = resMap + ("message" -> (event.jobId.toString))

        sendNotification(resMap)
      }

      override def onJobEnd(event: SparkListenerJobEnd): Unit = {
        var resMap: Map[String, Any] = new HashMap[String, Any]

        resMap = resMap + ("emitterType" -> "EXECUTION")
        resMap = resMap + ("emitterSubType" -> "DRIVER")
        resMap = resMap + ("emitterId" -> getApplicationName)
        val timestamp = new Date(event.time)
        resMap = resMap + ("timestamp" -> timestampFormat.format(timestamp))
        resMap = resMap + ("id" -> (getApplicationName + sc.startTime))
        resMap = resMap + ("workflowId" -> _workflowId)
        resMap = resMap + ("master" -> _master)
        resMap = resMap + ("actualDate" -> dateFormat.format(new Timestamp(dateFormat.parse(_jobParameters.getOrElse("actualDate", "1970-12-31").toString).getTime)))
        resMap = resMap + ("eventType" -> ("FINISH"))
        resMap = resMap + ("message" -> (event.jobId.toString))

        sendNotification(resMap)
      }

      override def onEnvironmentUpdate(event: SparkListenerEnvironmentUpdate): Unit = {
        //sendNotification("Environment Update " + event.environmentDetails)
        super.onEnvironmentUpdate(event)
      }

      override def onBlockManagerAdded(event: SparkListenerBlockManagerAdded): Unit = {
        //sendNotification("BlockManagerAdded " + event.blockManagerId)
        super.onBlockManagerAdded(event)
      }

      override def onBlockManagerRemoved(event: SparkListenerBlockManagerRemoved): Unit = {
        super.onBlockManagerRemoved(event)
      }

      override def onUnpersistRDD(event: SparkListenerUnpersistRDD): Unit = {
        super.onUnpersistRDD(event)
      }

      override def onApplicationStart(event: SparkListenerApplicationStart): Unit = {

      }

      override def onApplicationEnd(event: SparkListenerApplicationEnd): Unit = {

      }

      override def onExecutorMetricsUpdate(event: SparkListenerExecutorMetricsUpdate): Unit = {

      }

      override def onExecutorAdded(event: SparkListenerExecutorAdded): Unit = {
        //sendNotification("ExecutorAdded " + event.executorInfo)
        super.onExecutorAdded(event)
      }

      override def onExecutorRemoved(executorRemoved: SparkListenerExecutorRemoved): Unit = super.onExecutorRemoved(executorRemoved)

    })

    writeChildInfo(_fs)
    val startTime = System.currentTimeMillis()
    try {
      logger.logInfo("------->Start " + this.getClass.getName + ".runJob()")
      run(spark)
    } catch {
      case e: Throwable =>
        logger.logError(s"------->Exception while runJob(): ${e.getMessage}", e)
        throw new RuntimeException(e)
    } finally {
      logger.logInfo("------->Finish " + this.getClass.getName + ".runJob(). Elapsed time: " + (System.currentTimeMillis() - startTime))
    }
  }

}
