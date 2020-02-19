package zio.actors

import zio.Task
import zio.config.ConfigDescriptor
import zio.config.ConfigDescriptor._
import zio.config.typesafe.TypesafeConfig

private[actors] object ActorsConfig {

  final case class Addr(value: String) extends AnyVal
  final case class Port(value: Int)    extends AnyVal
  final case class RemoteConfig(addr: Addr, port: Port)

  val remoteConfig: ConfigDescriptor[String, String, Option[RemoteConfig]] =
    nested("remoting") {
      (string("hostname").xmap(Addr)(_.value) |@|
        int("port").xmap(Port)(_.value))(RemoteConfig.apply, RemoteConfig.unapply)
    }.optional

  private def selectiveSystemConfig[T](systemName: String, configT: ConfigDescriptor[String, String, T]) =
    nested(systemName) {
      nested("zio") {
        nested("actors") {
          configT
        }
      }
    }

  def getConfig[T](
    systemName: String,
    configStr: String,
    configDescriptor: ConfigDescriptor[String, String, T]
  ): Task[T] =
    for {
      config          <- TypesafeConfig.fromHocconString(configStr, selectiveSystemConfig(systemName, configDescriptor))
      unwrappedConfig <- config.config.config
    } yield unwrappedConfig

  def getRemoteConfig(systemName: String, configStr: String): Task[Option[RemoteConfig]] =
    getConfig(systemName, configStr, remoteConfig)

}
