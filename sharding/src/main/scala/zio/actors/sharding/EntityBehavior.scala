package zio.actors.sharding

trait EntityBehavior extends Entity with Behavior {

  type Msg = Behavior.Message[_, Command]

  def name: String = this.getClass.getSimpleName.replace('$', ' ').trim

}
