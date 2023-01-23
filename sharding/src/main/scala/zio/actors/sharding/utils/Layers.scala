package zio.actors.sharding.utils

import com.devsisters.shardcake.StorageRedis.Redis
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.pubsub.PubSub
import zio.actors.ActorSystem
import zio.interop.catz._
import zio.{ Task, ZEnvironment, ZIO, ZLayer }

import java.io.File

object Layers {

  val redis: ZLayer[Any, Throwable, Redis] =
    ZLayer.scopedEnvironment {
      implicit val runtime: zio.Runtime[Any] = zio.Runtime.default
      implicit val logger: Log[Task]         = new Log[Task] {
        override def debug(msg: => String): Task[Unit] = ZIO.logDebug(msg)
        override def error(msg: => String): Task[Unit] = ZIO.logError(msg)
        override def info(msg: => String): Task[Unit]  = ZIO.logInfo(msg)
      }

      (for {
        client   <- RedisClient[Task].from("redis://foobared@localhost")
        commands <- Redis[Task].fromClient(client, RedisCodec.Utf8)
        pubSub   <- PubSub.mkPubSubConnection[Task, String, String](client, RedisCodec.Utf8)
      } yield ZEnvironment(commands, pubSub)).toScopedZIO
    }

  case class ActorSystemZ(
    name: String,
    system: ActorSystem
  ) {
    val basePath = s"zio://$name@0.0.0.0:0000/"
  }

  def actorSystem(name: String, configPathName: Option[String] = None): ZLayer[Any, Throwable, ActorSystemZ] =
    ZLayer {
      ActorSystem(name, configPathName.map(path => new File(path))).map { system =>
        ActorSystemZ(name, system)
      }
    }

}
