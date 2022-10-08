package example

import com.devsisters.shardcake._
import com.devsisters.shardcake.interfaces.PodsHealth
import zio.actors.sharding.Behavior
import zio.actors.sharding.Behavior.Message
import zio.actors.sharding.utils.Layers
import zio.test.TestAspect.{ sequential, withLiveClock }
import zio.test.{ assertTrue, Spec, TestEnvironment, ZIOSpecDefault }
import zio.{ Random, Scope, ZIO }

object ShoppingCartEntitySpec extends ZIOSpecDefault {

  import ShoppingCart._

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("The Shopping Cart should")(
      test("complete cycle") {
        ZIO.scoped {
          for {
            _       <- Sharding.registerEntity(
                         ShoppingCartEntity.entityType,
                         Behavior.create(ShoppingCartEntity)
                       )
            _       <- Sharding.registerScoped
            cart    <- Sharding.messenger(ShoppingCartEntity.entityType)
            item1   <- Random.nextUUID.map(_.toString)
            item2   <- Random.nextUUID.map(_.toString)
            item3   <- Random.nextUUID.map(_.toString)
            _       <- cart.send[Confirmation]("cart1")(Message(AddItem(item1, 1), _)).debug
            _       <- cart.send[Confirmation]("cart1")(Message(AddItem(item2, 1), _)).debug
            _       <- cart.send[Confirmation]("cart1")(Message(RemoveItem(item1), _)).debug
            _       <- cart.send[Confirmation]("cart1")(Message(AddItem(item3, 1), _)).debug
            _       <- cart.send[Confirmation]("cart1")(Message(AdjustItemQuantity(item2, 2), _)).debug
            _       <- cart.send[Confirmation]("cart1")(Message(Checkout, _)).debug
            summary <- cart.send[Summary]("cart1")(Message(Get, _)).debug
          } yield assertTrue(summary == Summary(Map(item2 -> 2, item3 -> 1), checkedOut = true))
        }
      }
    ).provide(
      Sharding.live,
      KryoSerialization.live,
      GrpcPods.live,
      ShardManagerClient.liveWithSttp,
      StorageRedis.live,
      ShardManager.live,
      PodsHealth.noop,
      GrpcShardingService.live,
      TestLayers.shardManagerServer,
      TestLayers.container,
      TestLayers.redis,
      TestLayers.config,
      TestLayers.grpcConfig,
      TestLayers.managerConfig,
      TestLayers.redisConfig,
      Layers.actorSystem("testSys", Some("./sharding/src/test/resources/application.conf"))
    ) @@ sequential @@ withLiveClock
}
