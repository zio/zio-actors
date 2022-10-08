package example

import zio.actors.sharding.{ Behavior, Entity }
import zio.actors.persistence.EventSourcedStateful

object ShoppingCartEntity extends Entity with Behavior {
  type State       = ShoppingCart.State
  type Command[+A] = ShoppingCart.Command[A]
  type Event       = ShoppingCart.Event
  type Msg         = Behavior.Message[_, Command]

  def name: String = "ShoppingCartEntity"

  def stateEmpty: State = ShoppingCart.State.empty

  def eventSourcedFactory: String => EventSourcedStateful[Any, State, Command, Event] =
    persistenceId => ShoppingCart(persistenceId)

}
