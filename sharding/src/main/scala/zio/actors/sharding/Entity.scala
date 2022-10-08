package zio.actors.sharding

import com.devsisters.shardcake.EntityType

trait Entity {
  type Msg
  def name: String
  val entityType: EntityType[Msg] = new EntityType[Msg](name) {}
}
