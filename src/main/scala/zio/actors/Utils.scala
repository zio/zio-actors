package zio.actors

import java.io.File

import zio.Task
import zio.config.ConfigDescriptor._
import zio.config.typesafe.TypesafeConfig

private[actors] object Utils {

  case class Addr(value: String) extends AnyVal
  case class Port(value: Int)    extends AnyVal
  case class RemoteConfig(addr: Addr, port: Port)

  val remoteConfig =
    (string("hostname").xmap(Addr)(_.value) |@|
      int("port").xmap(Port)(_.value))(RemoteConfig.apply, RemoteConfig.unapply)

  val systemConfig = (systemName: String) =>
    nested(systemName) {
      nested("zio") {
        nested("actors") {
          nested("remoting") {
            remoteConfig
          }.optional
        }
      }
    }

  def getConfiguration(systemName: String, configFile: File): Task[Option[RemoteConfig]] =
    for {
      config          <- TypesafeConfig.fromHocconFile(systemConfig(systemName), configFile)
      unwrappedConfig <- config.config.config
    } yield unwrappedConfig

}
