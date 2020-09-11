package ru.neoflex.meta.etl

import java.sql.{Date, Timestamp}

/**
  * Created by orlov on 05.04.2016.
  */
object functions {
  def ABS(value: java.math.BigDecimal): java.math.BigDecimal = {
    value.abs()
  }
  def ABS(value: java.lang.Integer): java.lang.Integer = {
    java.lang.Math.abs(value.intValue())
  }
  def SIGN(value: java.math.BigDecimal): java.lang.Integer = {
    value.signum()
  }
  def SIGN(value: java.lang.Integer): java.lang.Integer = {
    if (value > 0) 1 else (if (value < 0)  -1 else 0)
  }
  def NVL(value1: AnyRef, value2: AnyRef): AnyRef = {
    if (DEFINED(value1)) value1 else value2
  }
  def NVL2(expr: AnyRef, value1: AnyRef, value2: AnyRef): AnyRef = {
    if (DEFINED(expr)) value1 else value2
  }
  def COALESCE(values: AnyRef*): AnyRef = {
    for (value<-values) {
      if (DEFINED(value)) {
        return value
      }
    }
    null
  }
  def DEFINED(value: AnyRef): Boolean = {
    return value != null && !value.equals(Nil)
  }
  def DECODE(expr: AnyRef, values: AnyRef*): AnyRef = {
    var even = false
    var defValue: AnyRef = null
    for (value<-values) {
      if (!even) {
        defValue = value
      }
      else {
        if (DEFINED(expr) && expr.equals(defValue) || !DEFINED(expr) && !DEFINED(defValue)) {
          return value
        }
        defValue = null
      }
      even = !even
    }
    defValue
  }
  def INTEGER(value: Int): java.lang.Integer = {
    new java.lang.Integer(value)
  }
  def INTEGER(value: java.math.BigDecimal): java.lang.Integer = {
    value.intValue()
  }
  def INTEGER(value: Double): java.lang.Integer = {
    value.intValue()
  }
  def INTEGER(value: String): java.lang.Integer = {
    java.lang.Integer.valueOf(value)
  }
  def INTEGER(value: String, radix: Int): java.lang.Integer = {
    java.lang.Integer.valueOf(value, radix)
  }
  def DECIMAL(value: Int): java.math.BigDecimal = {
    new java.math.BigDecimal(value)
  }
  def DECIMAL(value: java.math.BigDecimal): java.math.BigDecimal = {
    value
  }
  def DECIMAL(value: String): java.math.BigDecimal = {
    new java.math.BigDecimal(value)
  }
  def DECIMAL(value: Double): java.math.BigDecimal = {
    new java.math.BigDecimal(value)
  }
  def DATETIME(date_part: java.sql.Timestamp, hourOfDay: Int, minute: Int, second: Int, millisecond: Int): java.sql.Timestamp = {
    val calendar = java.util.Calendar.getInstance()
    calendar.setTime(date_part)
    calendar.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
    calendar.set(java.util.Calendar.MINUTE, minute)
    calendar.set(java.util.Calendar.SECOND, second)
    calendar.set(java.util.Calendar.MILLISECOND, millisecond)
    new java.sql.Timestamp(calendar.getTime().getTime())
  }
  def DATETIME(date_part: java.sql.Timestamp, time_part: java.sql.Timestamp): java.sql.Timestamp = {
    val calendar = java.util.Calendar.getInstance()
    calendar.setTime(time_part)
    DATETIME(date_part, calendar.get(java.util.Calendar.HOUR_OF_DAY), calendar.get(java.util.Calendar.MINUTE), calendar.get(java.util.Calendar.SECOND), calendar.get(java.util.Calendar.MILLISECOND))
  }
  def DATETIME(date: java.sql.Date, time_part: java.sql.Timestamp): java.sql.Timestamp = {
    DATETIME(DATETIME(date), time_part)
  }
  def DATETIME(year: Int, month: Int, dayOfMonth: Int, time_part: java.sql.Timestamp): java.sql.Timestamp = {
    val calendar = java.util.Calendar.getInstance()
    calendar.setTime(time_part)
    calendar.set(java.util.Calendar.YEAR, year)
    calendar.set(java.util.Calendar.MONTH, month - 1)
    calendar.set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)
    new java.sql.Timestamp(calendar.getTime().getTime())
  }
  def DATETIME(date: java.sql.Date): java.sql.Timestamp = {
    new java.sql.Timestamp(date.getTime)
  }
  def DATE_PART(timestamp: java.sql.Timestamp): java.sql.Timestamp = {
    DATETIME(timestamp, 0, 0, 0, 0)
  }
  def TIME_PART(timestamp: java.sql.Timestamp): java.sql.Timestamp = {
    DATETIME(1900, 1, 1, timestamp)
  }
  def DATE(timestamp: java.sql.Timestamp): java.sql.Date = {
    new java.sql.Date(DATE_PART(timestamp).getTime)
  }
  def TIME(timestamp: java.sql.Timestamp): java.sql.Timestamp = {
    DATETIME(0, 0, 0, timestamp)
  }
  def NOW(): java.sql.Timestamp = {
    new java.sql.Timestamp(new java.util.Date().getTime())
  }
  def TODAY(): java.sql.Date = {
    DATE(NOW())
  }
  def DATETIME(year: Int, month: Int, dayOfMonth: Int, hourOfDay: Int, minute: Int, second: Int, millisecond: Int): java.sql.Timestamp = {
    val calendar = java.util.Calendar.getInstance()
    calendar.set(java.util.Calendar.YEAR, year)
    calendar.set(java.util.Calendar.MONTH, month - 1)
    calendar.set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)
    calendar.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
    calendar.set(java.util.Calendar.MINUTE, minute)
    calendar.set(java.util.Calendar.SECOND, second)
    calendar.set(java.util.Calendar.MILLISECOND, millisecond)
    new java.sql.Timestamp(calendar.getTime().getTime())
  }
  def DATETIME(): java.sql.Timestamp = {
    DATETIME(1900, 1, 1, 0, 0, 0, 0)
  }
  def DATE(year: Int, month: Int, dayOfMonth: Int): java.sql.Date = {
    new java.sql.Date(DATETIME(year, month, dayOfMonth, 0, 0, 0, 0).getTime)
  }
  def TIME(hourOfDay: Int, minute: Int, second: Int, millisecond: Int): java.sql.Timestamp = {
    DATETIME(1900, 1, 1, hourOfDay, minute, second, millisecond)
  }
  def YEAR(timestamp: java.sql.Timestamp): Int = {
    val calendar = java.util.Calendar.getInstance()
    calendar.setTime(timestamp)
    calendar.get(java.util.Calendar.YEAR)
  }
  def MONTH(timestamp: java.sql.Timestamp): Int = {
    val calendar = java.util.Calendar.getInstance()
    calendar.setTime(timestamp)
    calendar.get(java.util.Calendar.MONTH) + 1
  }
  def DAY_OF_MONTH(timestamp: java.sql.Timestamp): Int = {
    val calendar = java.util.Calendar.getInstance()
    calendar.setTime(timestamp)
    calendar.get(java.util.Calendar.DAY_OF_MONTH)
  }
  def HOUR_OF_DAY(timestamp: java.sql.Timestamp): Int = {
    val calendar = java.util.Calendar.getInstance()
    calendar.setTime(timestamp)
    calendar.get(java.util.Calendar.HOUR_OF_DAY)
  }
  def MINUTE(timestamp: java.sql.Timestamp): Int = {
    val calendar = java.util.Calendar.getInstance()
    calendar.setTime(timestamp)
    calendar.get(java.util.Calendar.MINUTE)
  }
  def SECOND(timestamp: java.sql.Timestamp): Int = {
    val calendar = java.util.Calendar.getInstance()
    calendar.setTime(timestamp)
    calendar.get(java.util.Calendar.SECOND)
  }
  def MILLISECOND(timestamp: java.sql.Timestamp): Int = {
    val calendar = java.util.Calendar.getInstance()
    calendar.setTime(timestamp)
    calendar.get(java.util.Calendar.MILLISECOND)
  }
  def DATETIME(value: String): java.sql.Timestamp = {
    java.sql.Timestamp.valueOf(value)
  }
  def DATE_PART(value: String): java.sql.Timestamp = {
    DATETIME(s"${value} 00:00:00.0")
  }
  def DATE(value: String): java.sql.Date = {
    DATE(DATE_PART(value))
  }
  def TIME(value: String): java.sql.Timestamp = {
    DATETIME(s"1900-01-01 ${value}")
  }
  def DATE_STRING(date: java.sql.Date): String = {
    new java.text.SimpleDateFormat("yyyy-MM-dd").format(date)
  }
  def TIME_STRING(timestamp: java.sql.Timestamp): String = {
    new java.text.SimpleDateFormat("HH:mm:ss.S").format(timestamp)
  }
  def DATETIME_STRING(timestamp: java.sql.Timestamp): String = {
    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S").format(timestamp)
  }
  def ADD_DAYS(timestamp: java.sql.Timestamp, days: Int): java.sql.Timestamp = {
    val calendar = java.util.Calendar.getInstance()
    calendar.setTime(timestamp)
    calendar.add(java.util.Calendar.DAY_OF_WEEK, days)
    new java.sql.Timestamp(calendar.getTime().getTime())
  }
  def ADD_HOURS(timestamp: java.sql.Timestamp, hours: Int): java.sql.Timestamp = {
    val calendar = java.util.Calendar.getInstance()
    calendar.setTime(timestamp)
    calendar.add(java.util.Calendar.HOUR, hours)
    new java.sql.Timestamp(calendar.getTime().getTime())
  }
  def ADD_SECONDS(timestamp: java.sql.Timestamp, seconds: Int): java.sql.Timestamp = {
    val calendar = java.util.Calendar.getInstance()
    calendar.setTime(timestamp)
    calendar.add(java.util.Calendar.SECOND, seconds)
    new java.sql.Timestamp(calendar.getTime().getTime())
  }
  def SUBTRACT(t1: java.sql.Timestamp, t2: java.sql.Timestamp): java.math.BigDecimal = {
    var diffSeconds = (t1.getTime () / 1000) - (t2.getTime () / 1000);
    var diffNanos = t1.getNanos() - t2.getNanos();
    if (diffNanos < 0) {
      diffSeconds -= 1;
      diffNanos += 1000000000;
    }

    // mix nanos and millis again
    val result = new Timestamp ((diffSeconds * 1000) + (diffNanos / 1000000));
    // setNanos() with a value of in the millisecond range doesn't affect the value of the time field
    // while milliseconds in the time field will modify nanos! Damn, this API is a *mess*
    result.setNanos (diffNanos);
    new java.math.BigDecimal(result.getTime)
  }
  def DECIMAL(timestamp: java.sql.Timestamp): java.math.BigDecimal = {
    new java.math.BigDecimal(timestamp.getTime)
  }
  def ADD(timestamp: java.sql.Timestamp, shift: java.math.BigDecimal): java.sql.Timestamp = {
    new java.sql.Timestamp(timestamp.getTime + shift.longValueExact)
  }
}
