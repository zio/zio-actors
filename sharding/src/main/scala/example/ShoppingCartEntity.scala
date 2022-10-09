package example

import zio.actors.sharding.EntityBehavior

object ShoppingCartEntity extends EntityBehavior {

  type State = ShoppingCart.State

  type Command[+A] = ShoppingCart.Command[A]

  type Dependencies = Unit

  def stateEmpty: State = ShoppingCart.State.empty

  def actorFactory: Dependencies => String => Actor = _ => persistenceId => ShoppingCart(persistenceId)

}
