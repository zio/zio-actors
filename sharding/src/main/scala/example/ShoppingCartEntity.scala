package example

import zio.actors.sharding.{ Behavior, Entity }

object ShoppingCartEntity extends Entity {
  type Msg = Behavior.Message[_, ShoppingCartBehavior.Command]
  override def name: String = "ShoppingCartEntity"
}
