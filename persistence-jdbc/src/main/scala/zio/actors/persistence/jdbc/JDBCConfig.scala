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
      (string("url").transform[DbURL](DbURL, _.value) zip
        string("user").transform[DbUser](DbUser, _.value) zip
        string("pass").transform[DbPass](DbPass, _.value)).to[DbConfig]
    }

  def getDbConfig(systemName: String, configStr: String): Task[DbConfig] =
    getConfig(systemName, configStr, dbConfig)

}
