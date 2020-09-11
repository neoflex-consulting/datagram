package ru.neoflex.meta.etl2

import java.math
import java.sql.ResultSet

/**
  * Created by orlov on 20.02.2016.
  */
trait Sequence {
  def nextValue(): AnyRef

}

class LocalSequence extends Sequence with java.io.Serializable with scala.Serializable {
  var value: AnyRef = null;
  override def nextValue(): AnyRef = {
    if (value == null) {
      value = java.math.BigDecimal.ZERO
    }
    else {
      value = value.asInstanceOf[java.math.BigDecimal].add(java.math.BigDecimal.ONE)
    }
    value
  }
}

class OracleSequence(context: JdbcETLContext, sequenceName: String, batchSize: Int) extends Sequence with java.io.Serializable with scala.Serializable {
  var values: java.util.LinkedList[java.math.BigDecimal] = new java.util.LinkedList[java.math.BigDecimal]();
  override def nextValue(): AnyRef = {
    if (values.size() == 0) {
      val query: String =
        s"""
           |select $sequenceName.nextval
           |from (
           |   select level
           |   from dual
           |   connect by level < $batchSize
           |)
         """.stripMargin
      val cn = context.getConnection
      try {
        val stmt = cn.prepareCall(query)
        try {
          val rs = stmt.executeQuery();
          try {
            while (rs.next()) {
              val value = rs.getLong(1)
              values.push(new math.BigDecimal(value))
            }
          }
          finally {
            rs.close()
          }
        } finally {
          stmt.close()
        }
      } finally {
        cn.close()
      }
    }
    values.pop()
  }
}