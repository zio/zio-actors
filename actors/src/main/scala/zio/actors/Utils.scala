package zio.actors

import java.io.File

import zio.Task
import zio.config.ConfigDescriptor
import zio.config.ConfigDescriptor._
import zio.config.typesafe.TypesafeConfig

private[actors] object Utils {

  case class Addr(value: String) extends AnyVal
  case class Port(value: Int)    extends AnyVal
  case class RemoteConfig(addr: Addr, port: Port)

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
    configFile: File,
    configDescriptor: ConfigDescriptor[String, String, T]
  ): Task[T] =
    for {
      config          <- TypesafeConfig.fromHocconFile(selectiveSystemConfig(systemName, configDescriptor), configFile)
      unwrappedConfig <- config.config.config
    } yield unwrappedConfig

  def getRemoteConfig(systemName: String, configFile: File): Task[Option[RemoteConfig]] =
    getConfig(systemName, configFile, remoteConfig)

}
