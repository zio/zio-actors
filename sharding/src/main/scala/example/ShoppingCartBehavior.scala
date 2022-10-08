package example

import zio.actors.persistence.EventSourcedStateful
import zio.actors.sharding.Behavior

object ShoppingCartBehavior extends Behavior {
  type State       = ShoppingCart.State
  type Command[+A] = ShoppingCart.Command[A]
  type Event       = ShoppingCart.Event

  def stateEmpty: State = ShoppingCart.State.empty

  def eventSourcedFactory: String => EventSourcedStateful[Any, State, Command, Event] =
    persistenceId => ShoppingCart(persistenceId)
}
