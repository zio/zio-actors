package example

import com.devsisters.shardcake.interfaces.Serialization
import com.devsisters.shardcake._
import zio.actors.sharding.Behavior
import zio.actors.sharding.utils.Layers
import zio.actors.sharding.utils.Layers.ActorSystemZ
import zio.{ Random, Scope, System, Task, ZIO, ZIOAppDefault, ZLayer }

object ShoppingCartApp extends ZIOAppDefault {

  val config: ZLayer[Any, SecurityException, Config] =
    ZLayer(
      System
        .env("port")
        .map(_.flatMap(_.toIntOption).fold(Config.default)(port => Config.default.copy(shardingPort = port)))
    )

  import zio.actors.sharding.utils.MessengerOps._
  import ShoppingCart._

  val program: ZIO[
    Sharding with ActorSystemZ with Scope with Serialization,
    Throwable,
    Unit
  ] = {
    for {
      _     <- Sharding.registerEntity(
                 ShoppingCartEntity.entityType,
                 Behavior.create(ShoppingCartEntity)(())
               )
      _     <- Sharding.registerScoped
      cart  <- Sharding.messenger(ShoppingCartEntity.entityType)
      item1 <- Random.nextUUID.map(_.toString)
      item2 <- Random.nextUUID.map(_.toString)
      item3 <- Random.nextUUID.map(_.toString)
      _     <- cart.ask("cart1")(AddItem(item1, 1)).debug
      _     <- cart.ask("cart1")(AddItem(item2, 1)).debug
      _     <- cart.ask("cart1")(RemoveItem(item1)).debug
      _     <- cart.ask("cart1")(AddItem(item3, 1)).debug
      _     <- cart.ask("cart1")(AdjustItemQuantity(item2, 2)).debug
      _     <- cart.ask("cart1")(Checkout).debug
      _     <- cart.ask("cart1")(Get).debug
      _     <- ZIO.never
    } yield ()
  }

  def run: Task[Unit] =
    ZIO
      .scoped(program)
      .provide(
        config,
        ZLayer.succeed(GrpcConfig.default),
        ZLayer.succeed(RedisConfig.default),
        Layers.redis,
        Layers.actorSystem("ShoppingCartSystem", Some("./sharding/src/main/resources/application.conf")),
        StorageRedis.live,
        KryoSerialization.live,
        ShardManagerClient.liveWithSttp,
        GrpcPods.live,
        Sharding.live,
        GrpcShardingService.live
      )

}
