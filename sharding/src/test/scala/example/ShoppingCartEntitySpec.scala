package example

import com.devsisters.shardcake._
import com.devsisters.shardcake.interfaces.PodsHealth
import zio.actors.sharding.Behavior
import zio.actors.sharding.utils.Layers
import zio.test.TestAspect.{ sequential, withLiveClock }
import zio.test.{ assertTrue, Spec, TestEnvironment, ZIOSpecDefault }
import zio.{ Random, Scope, ZIO }

object ShoppingCartEntitySpec extends ZIOSpecDefault {

  import zio.actors.sharding.utils.MessengerOps._
  import ShoppingCart._

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("The Shopping Cart should")(
      test("complete cycle") {
        ZIO.scoped {
          for {
            _       <- Sharding.registerEntity(
                         ShoppingCartEntity.entityType,
                         Behavior.create(ShoppingCartEntity)(())
                       )
            _       <- Sharding.registerScoped
            cart    <- Sharding.messenger(ShoppingCartEntity.entityType)
            item1   <- Random.nextUUID.map(_.toString)
            item2   <- Random.nextUUID.map(_.toString)
            item3   <- Random.nextUUID.map(_.toString)
            _       <- cart.ask("cart1")(AddItem(item1, 1)).debug
            _       <- cart.ask("cart1")(AddItem(item2, 1)).debug
            _       <- cart.ask("cart1")(RemoveItem(item1)).debug
            _       <- cart.ask("cart1")(AddItem(item3, 1)).debug
            _       <- cart.ask("cart1")(AdjustItemQuantity(item2, 2)).debug
            _       <- cart.ask("cart1")(Checkout).debug
            summary <- cart.ask("cart1")(Get).debug
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
