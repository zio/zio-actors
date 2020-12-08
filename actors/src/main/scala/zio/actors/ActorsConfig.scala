package zio.actors

import zio.actors.config.{ Addr, Port, RemoteConfig }
import zio.{ Task, ZIO }
import zio.config.{ ConfigDescriptor, ZConfig }
import zio.config.ConfigDescriptor._
import zio.config.typesafe.TypesafeConfig
import zio.Tag

private[actors] object ActorsConfig {

  val remoteConfig: ConfigDescriptor[Option[RemoteConfig]] =
    nested("remoting") {
      (string("hostname").xmap[Addr](Addr, _.value) |@|
        int("port").xmap[Port](Port, _.value))(RemoteConfig.apply, RemoteConfig.unapply)
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
    ZIO
      .access[ZConfig[T]](_.get)
      .provideLayer(TypesafeConfig.fromHoconString[T](configStr, selectiveSystemConfig(systemName, configDescriptor)))

  def getRemoteConfig(systemName: String, configStr: String): Task[Option[RemoteConfig]] =
    getConfig(systemName, configStr, remoteConfig)

}
