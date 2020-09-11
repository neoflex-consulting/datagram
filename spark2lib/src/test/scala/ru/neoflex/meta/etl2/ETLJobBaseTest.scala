package ru.neoflex.meta.etl2
import org.apache.spark.sql.SparkSession
import org.junit.Assert
import org.junit.Test

class ETLJobBaseTest extends ETLJobBase {
  override def getApplicationName: String = ???

  override def run(spark: SparkSession): Any = ???

  @Test def testParse(): Unit = {

    val testUrlSid = "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS_LIST=(ADDRESS=(PROTOCOL=TCP)(HOST=10.50.72.5)(PORT=1521)))(CONNECT_DATA=(SERVICE_NAME=nrdevext)))"
    val testUrlPlain = "jdbc:oracle:thin:@10.50.72.5:1521/nrdevext"

    val ctxSidName = "nccNRDMA"
    val ctxPlainName = "nccNRDMAPLAIN"

    val testParams: Array[String] = Array(s"JDBC_${ctxSidName}_URL=${testUrlSid}", s"JDBC_${ctxPlainName}_URL=${testUrlPlain}")
    this.parse(testParams.toSeq)

    var ctx: JdbcETLContext = this._contexts(ctxSidName).asInstanceOf[JdbcETLContext]
    Assert.assertEquals(testUrlSid, ctx._url)

    ctx = this._contexts(ctxPlainName).asInstanceOf[JdbcETLContext]
    Assert.assertEquals(testUrlPlain, ctx._url)

  }
}

object ETLJobBaseTest {
  def main(args: Array[String]): Unit = {
    val test = new ETLJobBaseTest()
    test.testParse()

  }
}
