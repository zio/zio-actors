package example

import com.devsisters.shardcake._
import com.devsisters.shardcake.interfaces.Serialization
import zio.actors.sharding.Behavior
import zio.actors.sharding.Behavior.Message
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

  import ShoppingCart._
  val program: ZIO[
    Sharding with ActorSystemZ with Scope with Serialization,
    Throwable,
    Unit
  ] = {
    for {
      _     <- Sharding.registerEntity(
                 ShoppingCartEntity.entityType,
                 Behavior.create(ShoppingCartEntity)
               )
      _     <- Sharding.registerScoped
      cart  <- Sharding.messenger(ShoppingCartEntity.entityType)
      item1 <- Random.nextUUID.map(_.toString)
      item2 <- Random.nextUUID.map(_.toString)
      item3 <- Random.nextUUID.map(_.toString)
      _     <- cart.send[Confirmation]("cart1")(Message(AddItem(item1, 1), _)).debug
      _     <- cart.send[Confirmation]("cart1")(Message(AddItem(item2, 1), _)).debug
      _     <- cart.send[Confirmation]("cart1")(Message(RemoveItem(item1), _)).debug
      _     <- cart.send[Confirmation]("cart1")(Message(AddItem(item3, 1), _)).debug
      _     <- cart.send[Confirmation]("cart1")(Message(AdjustItemQuantity(item2, 2), _)).debug
      _     <- cart.send[Confirmation]("cart1")(Message(Checkout, _)).debug
      _     <- cart.send[Summary]("cart1")(Message(Get, _)).debug
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
