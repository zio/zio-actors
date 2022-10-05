package zio.actors.sharding

import com.devsisters.shardcake._
import com.devsisters.shardcake.interfaces.PodsHealth
import zio.{ Task, ZIOAppDefault, ZLayer }

object ShardManagerApp extends ZIOAppDefault {
  def run: Task[Nothing] =
    Server.run.provide(
      ZLayer.succeed(ManagerConfig.default),
      ZLayer.succeed(GrpcConfig.default),
      ZLayer.succeed(RedisConfig.default),
      Layers.redis,
      StorageRedis.live, // store data in Redis
      PodsHealth.local,  // just ping a pod to see if it's alive
      GrpcPods.live,     // use gRPC protocol
      ShardManager.live  // Shard Manager logic
    )
}
