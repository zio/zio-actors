package zio.actors

import zio._
import zio.config.{ getConfig => zioGetConfig }
import zio.config._

import zio.config.ConfigDescriptor._
import zio.config.typesafe.TypesafeConfig
import zio.Tag

private[actors] object ActorsConfig {

  final case class Addr(value: String) extends AnyVal
  final case class Port(value: Int)    extends AnyVal
  final case class RemoteConfig(addr: Addr, port: Port)

  val remoteConfig: ConfigDescriptor[Option[RemoteConfig]] =
    nested("remoting") {
      (string("hostname").transform[Addr](Addr, _.value) zip
        int("port").transform[Port](Port, _.value)).to[RemoteConfig]
    }.optional

  private def selectiveSystemConfig[T](systemName: String, configT: ConfigDescriptor[T]) =
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
    configDescriptor: ConfigDescriptor[T]
  )(implicit tag: Tag[T]): Task[T] =
    zioGetConfig[T]
      .provideLayer(TypesafeConfig.fromHoconString[T](configStr, selectiveSystemConfig(systemName, configDescriptor)))

  def getRemoteConfig(systemName: String, configStr: String): Task[Option[RemoteConfig]] =
    getConfig(systemName, configStr, remoteConfig)

}
