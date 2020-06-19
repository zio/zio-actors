package zio.actors.persistence.jdbc

import zio.Task
import zio.actors.ActorsConfig.getConfig
import zio.config.ConfigDescriptor
import zio.config.ConfigDescriptor.{ nested, string }

private[actors] object JDBCConfig {

  final case class DbURL(value: String)  extends AnyVal
  final case class DbUser(value: String) extends AnyVal
  final case class DbPass(value: String) extends AnyVal
  final case class DbConfig(dbURL: DbURL, dbUser: DbUser, dbPass: DbPass)

  val dbConfig: ConfigDescriptor[DbConfig] =
    nested("persistence") {
      (string("url").xmap[DbURL](DbURL, _.value) |@|
        string("user").xmap[DbUser](DbUser, _.value) |@|
        string("pass").xmap[DbPass](DbPass, _.value))(DbConfig.apply, DbConfig.unapply)
    }

  def getDbConfig(systemName: String, configStr: String): Task[DbConfig] =
    getConfig(systemName, configStr, dbConfig)

}
