package example

import zio.actors.persistence.EventSourcedStateful
import zio.actors.sharding.EntityBehavior

object ShoppingCartEntity extends EntityBehavior {

  type State = ShoppingCart.State

  type Command[+A] = ShoppingCart.Command[A]

  type Event = ShoppingCart.Event

  def name: String = "ShoppingCartEntity"

  def stateEmpty: State = ShoppingCart.State.empty

  def eventSourcedFactory: String => EventSourcedStateful[Any, State, Command, Event] = ShoppingCart.apply

}
