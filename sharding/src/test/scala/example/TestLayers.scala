package example

import com.devsisters.shardcake.StorageRedis.Redis
import com.devsisters.shardcake._
import com.dimafeng.testcontainers.GenericContainer
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.pubsub.PubSub
import sttp.client3.UriContext
import zio.Clock.ClockLive
import zio.interop.catz._
import zio.{ durationInt, Runtime, Task, ULayer, ZEnvironment, ZIO, ZLayer }

object TestLayers {

  val shardManagerServer: ZLayer[ShardManager with ManagerConfig, Throwable, Unit] =
    ZLayer(Server.run.forkDaemon *> ClockLive.sleep(3.seconds).unit)

  val container: ZLayer[Any, Nothing, GenericContainer] =
    ZLayer.scoped {
      ZIO.acquireRelease {
        ZIO.attemptBlocking {
          val container = new GenericContainer(dockerImage = "redis:6.2.5", exposedPorts = Seq(6379))
          container.start()
          container
        }.orDie
      }(container => ZIO.attemptBlocking(container.stop()).orDie)
    }

  val redis: ZLayer[GenericContainer, Throwable, Redis] =
    ZLayer.scopedEnvironment {
      implicit val runtime: Runtime[Any] = Runtime.default
      implicit val logger: Log[Task]     = new Log[Task] {
        override def debug(msg: => String): Task[Unit] = ZIO.unit

        override def error(msg: => String): Task[Unit] = ZIO.logError(msg)

        override def info(msg: => String): Task[Unit] = ZIO.logDebug(msg)
      }
      ZIO
        .service[GenericContainer]
        .flatMap(container =>
          (for {
            client   <- RedisClient[Task].from(
                          s"redis://foobared@${container.host}:${container.mappedPort(container.exposedPorts.head)}"
                        )
            commands <- Redis[Task].fromClient(client, RedisCodec.Utf8)
            pubSub   <- PubSub.mkPubSubConnection[Task, String, String](client, RedisCodec.Utf8)
          } yield ZEnvironment(commands, pubSub)).toScopedZIO
        )
    }

  val config: ULayer[Config]         = ZLayer.succeed(
    Config.default.copy(
      shardManagerUri = uri"http://localhost:8087/api/graphql",
      simulateRemotePods = true,
      sendTimeout = 3.seconds
    )
  )
  val grpcConfig: ULayer[GrpcConfig] = ZLayer.succeed(GrpcConfig.default)

  val managerConfig: ULayer[ManagerConfig] = ZLayer.succeed(ManagerConfig.default.copy(apiPort = 8087))

  val redisConfig: ULayer[RedisConfig] = ZLayer.succeed(RedisConfig.default)
}
