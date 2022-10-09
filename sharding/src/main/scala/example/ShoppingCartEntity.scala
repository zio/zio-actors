package example

import zio.actors.sharding.EntityBehavior

object ShoppingCartEntity extends EntityBehavior {

  type State = ShoppingCart.State

  type Command[+A] = ShoppingCart.Command[A]

  def name: String = "ShoppingCartEntity"

  def stateEmpty: State = ShoppingCart.State.empty

  def actorFactory: String => Actor = ShoppingCart.apply

}
