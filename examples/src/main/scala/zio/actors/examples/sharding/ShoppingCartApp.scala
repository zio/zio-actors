package zio.actors.examples.sharding

import com.devsisters.shardcake._
import com.devsisters.shardcake.interfaces.Serialization
import zio.actors.sharding.Layers
import zio.actors.sharding.Layers.ActorSystemZ
import zio.{ Random, Scope, System, Task, ZIO, ZIOAppDefault, ZLayer }

object ShoppingCartApp extends ZIOAppDefault {
  val config: ZLayer[Any, SecurityException, Config] =
    ZLayer(
      System
        .env("port")
        .map(_.flatMap(_.toIntOption).fold(Config.default)(port => Config.default.copy(shardingPort = port)))
    )

  val program: ZIO[
    Sharding with ActorSystemZ with Scope with Serialization,
    Throwable,
    Unit
  ] =
    for {
      _     <- Sharding.registerEntity(
                 ShoppingCartEntity.ShoppingCartEntityType,
                 ShoppingCartBehavior.behavior
               )
      _     <- Sharding.registerScoped
      cart  <- Sharding.messenger(ShoppingCartEntity.ShoppingCartEntityType)
      item1 <- Random.nextUUID.map(_.toString)
      item2 <- Random.nextUUID.map(_.toString)
      item3 <- Random.nextUUID.map(_.toString)
      _     <- cart.send("cart1")(ShoppingCartEntity.AddItem(item1, 1, _)).debug
      _     <- cart.send("cart1")(ShoppingCartEntity.AddItem(item2, 1, _)).debug
      _     <- cart.send("cart1")(ShoppingCartEntity.RemoveItem(item1, _)).debug
      _     <- cart.send("cart1")(ShoppingCartEntity.AddItem(item3, 1, _)).debug
      _     <- cart.send("cart1")(ShoppingCartEntity.AdjustItemQuantity(item2, 2, _)).debug
      _     <- cart.send("cart1")(ShoppingCartEntity.Checkout).debug
      _     <- cart.send("cart1")(ShoppingCartEntity.Get).debug
      _     <- ZIO.never
    } yield ()

  def run: Task[Unit] =
    ZIO
      .scoped(program)
      .provide(
        config,
        ZLayer.succeed(GrpcConfig.default),
        ZLayer.succeed(RedisConfig.default),
        Layers.redis,
        Layers.actorSystem("ShoppingCartSystem"),
        StorageRedis.live,
        KryoSerialization.live,
        ShardManagerClient.liveWithSttp,
        GrpcPods.live,
        Sharding.live,
        GrpcShardingService.live
      )
}
