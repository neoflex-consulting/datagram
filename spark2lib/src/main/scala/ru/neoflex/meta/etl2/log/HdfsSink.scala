package ru.neoflex.meta.etl2.log

import java.nio.charset.Charset
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{Path, FileSystem, FSDataOutputStream}
import org.apache.hadoop.hdfs.DistributedFileSystem

class HdfsSink(logDir: String, appId: String) extends LogSink {

  @transient lazy val _outStream: FSDataOutputStream = initIfRequired()

  override def logMessage(msg: String): Unit = {
    val s = msg + "\r\n"
    _outStream.write(s.getBytes(Charset.forName("UTF8")))
    _outStream.hsync()
  }

  private def initIfRequired(): FSDataOutputStream = {
    val fs = makeFileSystem().asInstanceOf[DistributedFileSystem]
    val logPathName = s"$logDir/${appId}/logs.json"
    val logPath = new Path(logPathName)
    try {
      fs.append(logPath)
    }
    catch {
      case e: java.io.FileNotFoundException => fs.create(logPath)
      case e: org.apache.hadoop.ipc.RemoteException if (e.unwrapRemoteException().isInstanceOf[org.apache.hadoop.hdfs.protocol.AlreadyBeingCreatedException]) =>
        println(s"trying to recover lease on ${logPathName}")
        fs.recoverLease(logPath)
        var isClosed = fs.isFileClosed(logPath)
        val startTime = System.currentTimeMillis()
        while(!isClosed) {
          if(System.currentTimeMillis() - startTime > 60*1000)
            throw e;
          try {
            println(s"waiting for lease on ${logPathName}")
            Thread.sleep(1000);
          } catch {
            case e: InterruptedException => {}
          }
          isClosed = fs.isFileClosed(logPath);
        }
        println(s"file ${logPathName} is closed")
        fs.append(logPath)
    }
    //if (fs.exists(logPath))
    //  fs.append(logPath)
    //else
    //  fs.create(logPath)
  }

  private def makeFileSystem(): FileSystem = {
    val hadoopConfDir = System.getenv("HADOOP_CONF_DIR")
    val config = new Configuration()
    config.addResource(new Path(s"$hadoopConfDir/core-site.xml"))
    config.addResource(new Path(s"$hadoopConfDir/hdfs-site.xml"))

    FileSystem.get(config)
  }
}
