package zio.actors.persistence.cassandra

import zio.Task
import zio.actors.ActorsConfig.getConfig
import zio.config.ConfigDescriptor
import zio.config.ConfigDescriptor.{ nested, string, int, boolean}

private[actors] object CassandraConfig {

  final case class DbHost(value: String)  extends AnyVal
  final case class DbPort(value: Int) extends AnyVal
  final case class DbKeyspace(value: String) extends AnyVal
  final case class DbDataCenter(value: String) extends AnyVal
  final case class DbShouldCreateKeyspace(value: Boolean) extends AnyVal
  final case class DbConfig(host: DbHost, port: DbPort, keyspace: DbKeyspace, datacenter: DbDataCenter, shouldCreateKeyspace: DbShouldCreateKeyspace)

  val dbConfig: ConfigDescriptor[DbConfig] =
    nested("persistence-cassandra") {
      (string("host").xmap[DbHost](DbHost, _.value) |@|
        int("port").xmap[DbPort](DbPort, _.value) |@|
        string("keyspace").xmap[DbKeyspace](DbKeyspace, _.value) |@|
        string("datacenter").xmap[DbDataCenter](DbDataCenter, _.value) |@|
        boolean("shouldCreateKeyspace").xmap[DbShouldCreateKeyspace](DbShouldCreateKeyspace, _.value)
        )(DbConfig.apply, DbConfig.unapply)
    }

  def getDbConfig(systemName: String, configStr: String): Task[DbConfig] =
    getConfig(systemName, configStr, dbConfig)

}
