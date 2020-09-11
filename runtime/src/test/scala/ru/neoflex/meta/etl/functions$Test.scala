package ru.neoflex.meta.etl

import java.math.BigDecimal

import org.scalatest.FunSuite
import ru.neoflex.meta.etl.functions._

/**
  * Created by orlov on 14.04.2016.
  */
class functions$Test extends FunSuite {
  test("abs(BigDecimal)") {
    assert(ABS(new BigDecimal(-1)).compareTo(new BigDecimal(1)) == 0)
    assert(ABS(new BigDecimal(22)).compareTo(new BigDecimal(22)) == 0)
    assert(ABS(new BigDecimal(0)).compareTo(new BigDecimal(0)) == 0)
  }
  test("abs(Integer)") {
    assert(ABS(new Integer(-1)).compareTo(new Integer(1)) == 0)
    assert(ABS(new Integer(22)).compareTo(new Integer(22)) == 0)
    assert(ABS(new Integer(0)).compareTo(new Integer(0)) == 0)
  }
  test("sign(BigDecimal)") {
    assert(SIGN(new BigDecimal(-2)).compareTo(new Integer(-1)) == 0)
    assert(SIGN(new BigDecimal(22)).compareTo(new Integer(1)) == 0)
    assert(SIGN(new BigDecimal(0)).compareTo(new Integer(0)) == 0)
  }
  test("sign(Integer)") {
    assert(SIGN(new Integer(-2)).compareTo(new Integer(-1)) == 0)
    assert(SIGN(new Integer(22)).compareTo(new Integer(1)) == 0)
    assert(SIGN(new Integer(0)).compareTo(new Integer(0)) == 0)
  }
  test("nvl(...)") {
    assert(NVL(null, new Integer(1)).equals(new Integer(1)))
    assert(NVL(Nil, new Integer(3)).equals(new Integer(3)))
    assert(NVL(new Integer(1), new Integer(2)).equals(new Integer(1)))
  }
  test("nvl2(...)") {
    assert(NVL2(null, new Integer(1), new Integer(2)).equals(new Integer(2)))
    assert(NVL2(Nil, new Integer(1), new Integer(2)).equals(new Integer(2)))
    assert(NVL2("", new Integer(1), new Integer(2)).equals(new Integer(1)))
  }
  test("coalesce(...)") {
    assert(COALESCE(null, new Integer(1), new Integer(2)).equals(new Integer(1)))
    assert(COALESCE(Nil, new Integer(1), new Integer(2)).equals(new Integer(1)))
    assert(COALESCE("", new Integer(1), new Integer(2)).equals(""))
    assert(COALESCE() == null)
  }
  test("defined(...)") {
    assert(DEFINED(null) == false)
    assert(DEFINED(Nil) == false)
    assert(DEFINED("") == true)
  }
  test("decode(...)") {
    assert(DECODE(null, "1", "2", "3").equals("3"))
    assert(DECODE(null, Nil, "2", "3").equals("2"))
    assert(DECODE("7", "1", "2", "3", "4", "5", "6") == null)
    assert(DECODE("7") == null)
    assert(DECODE("8", "1", "2", "3", "4", "5", "6", "7")equals("7"))
    assert(DECODE("5", "1", "2", "3", "4", "5", "6", "7")equals("6"))
    assert(DECODE("5", "1")equals("1"))
  }
  test("DATETIME(...)") {
    val ts = DATETIME(2016, 4, 15, 13, 38, 45, 999)
    val dt = DATE(2016, 4, 15)
    val tsdt = DATE(ts)
    assert(tsdt.equals(dt))
  }
  test("DATETIME_STRING(...)") {
    val ts = DATETIME(2016, 4, 15, 13, 38, 45, 999)
    val tss = DATETIME_STRING(ts)
    assert("2016-04-15 13:38:45.999".equals(tss))
  }
  test("DATE_STRING(...)") {
    val ts = DATE(2016, 4, 15)
    val tss = DATE_STRING(ts)
    assert("2016-04-15".equals(tss))
  }
  test("TIME_STRING(...)") {
    val ts = TIME(13, 38, 45, 999)
    val tss = TIME_STRING(ts)
    assert("13:38:45.999".equals(tss))
  }
  test("DATETIME(String)") {
    assert(DATETIME("2016-04-15 13:38:45.999").equals(DATETIME(2016, 4, 15, 13, 38, 45, 999)))
    assert(DATETIME("2016-04-15 13:38:45.999").equals(DATETIME(DATE(2016, 4, 15), TIME(13, 38, 45, 999))))
    assert(DATETIME("2016-04-15 13:38:45").equals(DATETIME(2016, 4, 15, 13, 38, 45, 0)))
    assert(DATE("2016-04-15").equals(DATE(2016, 4, 15)))
    assert(TIME("13:38:45.999").equals(TIME(13, 38, 45, 999)))
  }
  test("ADD(...)") {
    val z = DATETIME();
    val ts = DATETIME(2016, 4, 15, 13, 38, 45, 999)
    val shift = SUBTRACT(ts, z)
    val result = ADD(z, shift)
    assert(result.equals(ts))
    val shift2 = DECIMAL(ts).subtract(DECIMAL(z))
    val result2 = ADD(z, shift2)
    assert(result2.equals(ts))
  }
  test("INTEGER(...)") {
    assert(INTEGER(1).equals(1))
    assert(INTEGER(DECIMAL(1.0)).equals(1))
    assert(INTEGER(1.0).equals(1))
    assert(INTEGER("1").equals(1))
    assert(INTEGER("111", 2).equals(7))
  }
}
