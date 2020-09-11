package ru.neoflex.meta.etl2

import java.math.BigDecimal
import java.sql.Timestamp
import java.text.SimpleDateFormat

/**
 * @author volkov
 */
object ETLJobConst {
  val DECIMAL_YES = new BigDecimal(1)
  val DECIMAL_NO = new BigDecimal(0)
  val MIN_ID: Long = 0L
  val DEFAULT_BIGDECIMAL: math.BigDecimal = DECIMAL_NO
  val MAX_ID: Long = 99999999999999L
  val DEFAULT_STRING = "-!-"
  val DEFAULT_NUMBER = new BigDecimal(-987654321)
  val SCALA_DEFAULT_NUMBER = new math.BigDecimal(DEFAULT_NUMBER)
  val sdf = new SimpleDateFormat("yyyy-MM-dd")
  val DEFAULT_TIMESTAMP = new Timestamp(sdf.parse("1899-12-31").getTime)
  val DEFAULT_DATE = DEFAULT_TIMESTAMP
  val DEFAULT_INTEGER = new java.lang.Integer(0)
  val DEFAULT_INT = DEFAULT_INTEGER

  val AUD_TYPE = "f"
  val IMPORT_SESSION_ID: java.math.BigDecimal = new java.math.BigDecimal(0)
  val PARTY_TYPE = "N"
}