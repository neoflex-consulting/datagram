package ru.neoflex.meta.etl2

import java.io.Serializable
import java.sql.{Connection, Driver, DriverManager}

/**
 * Created by kostas on 04.08.2015.
 */
class JdbcETLContext(override val _name : String) extends ETLContext(_name : String) with Serializable with scala.Serializable{
  var _url : String = ""
  var _schema : String = ""
  var _user : String = ""
  var _password : String = ""
  var _driverClassName : String = ""

  def getConnection : Connection = {
    val z : Driver = Class.forName(_driverClassName).newInstance.asInstanceOf[Driver]
    DriverManager.registerDriver(z)
    println(s"---------->DriverManager=" + z)
    println(s"---------->getConnection(" + _url + ", " + _user + ", ******")
    DriverManager.getConnection(_url, _user, _password)
  }

}

class ETLContext(val _name : String) extends Serializable with scala.Serializable{


}
