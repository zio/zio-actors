package zio.actors.sharding

trait EntityBehavior extends Entity with Behavior {
  type Msg = Behavior.Message[_, Command]
}
