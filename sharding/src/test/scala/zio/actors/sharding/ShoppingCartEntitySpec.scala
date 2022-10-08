package zio.actors.sharding

import com.devsisters.shardcake.StorageRedis.Redis
import com.devsisters.shardcake._
import com.devsisters.shardcake.interfaces.PodsHealth
import com.dimafeng.testcontainers.GenericContainer
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.pubsub.PubSub
import example.ShoppingCart.Summary
import example.{ ShoppingCartBehavior, ShoppingCartEntity }
import sttp.client3.UriContext
import zio.Clock.ClockLive
import zio._
import zio.interop.catz._
import zio.test.TestAspect.{ sequential, withLiveClock }
import zio.test.{ assertTrue, Spec, TestEnvironment, ZIOSpecDefault }

object ShoppingCartEntitySpec extends ZIOSpecDefault {

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
        override def info(msg: => String): Task[Unit]  = ZIO.logDebug(msg)
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

  private val config        = ZLayer.succeed(
    Config.default.copy(
      shardManagerUri = uri"http://localhost:8087/api/graphql",
      simulateRemotePods = true,
      sendTimeout = 3.seconds
    )
  )
  private val grpcConfig    = ZLayer.succeed(GrpcConfig.default)
  private val managerConfig = ZLayer.succeed(ManagerConfig.default.copy(apiPort = 8087))
  private val redisConfig   = ZLayer.succeed(RedisConfig.default)

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("The Shopping Cart should")(
      test("complete cycle") {
        ZIO.scoped {
          for {
            _       <- Sharding.registerEntity(
                         ShoppingCartEntity.entityType,
                         Behavior.create(ShoppingCartBehavior.behavior)
                       )
            _       <- Sharding.registerScoped
            cart    <- Sharding.messenger(ShoppingCartEntity.entityType)
            item1   <- Random.nextUUID.map(_.toString)
            item2   <- Random.nextUUID.map(_.toString)
            item3   <- Random.nextUUID.map(_.toString)
            _       <- cart.send("cart1")(ShoppingCartEntity.AddItem(item1, 1, _))
            _       <- cart.send("cart1")(ShoppingCartEntity.AddItem(item2, 1, _))
            _       <- cart.send("cart1")(ShoppingCartEntity.RemoveItem(item1, _))
            _       <- cart.send("cart1")(ShoppingCartEntity.AddItem(item3, 1, _))
            _       <- cart.send("cart1")(ShoppingCartEntity.AdjustItemQuantity(item2, 2, _))
            _       <- cart.send("cart1")(ShoppingCartEntity.Checkout)
            summary <- cart.send("cart1")(ShoppingCartEntity.Get)
          } yield assertTrue(summary == Summary(Map(item2 -> 2, item3 -> 1), checkedOut = true))
        }
      }
    ).provideShared(
      Sharding.live,
      KryoSerialization.live,
      GrpcPods.live,
      ShardManagerClient.liveWithSttp,
      StorageRedis.live,
      ShardManager.live,
      PodsHealth.noop,
      GrpcShardingService.live,
      shardManagerServer,
      container,
      redis,
      config,
      grpcConfig,
      managerConfig,
      redisConfig,
      Layers.actorSystem("testSys", Some("./sharding/src/test/resources/application.conf"))
    ) @@ sequential @@ withLiveClock
}
