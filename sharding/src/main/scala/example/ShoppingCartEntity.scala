package example

import com.devsisters.shardcake.EntityType
import com.devsisters.shardcake.Messenger.Replier
import com.devsisters.shardcake.Sharding
import zio.ZIO
import zio.actors.ActorRef
import zio.actors.persistence.EventSourcedStateful
import zio.actors.sharding.Behavior

object ShoppingCartEntity extends Behavior {

  sealed trait Message

  final case class AddItem(
    itemId: String,
    quantity: Int,
    replier: Replier[ShoppingCart.Confirmation]
  ) extends Message

  final case class RemoveItem(
    itemId: String,
    replier: Replier[ShoppingCart.Confirmation]
  ) extends Message

  final case class AdjustItemQuantity(
    itemId: String,
    quantity: Int,
    replier: Replier[ShoppingCart.Confirmation]
  ) extends Message

  final case class Checkout(
    replier: Replier[ShoppingCart.Confirmation]
  ) extends Message

  final case class Get(
    replier: Replier[ShoppingCart.Summary]
  ) extends Message

  object ShoppingCartEntityType extends EntityType[Message]("ShoppingCartEntity")

  type BehaviorMessage = Message
  type State           = ShoppingCart.State
  type Command[+A]     = ShoppingCart.Command[A]
  type Event           = ShoppingCart.Event

  def stateEmpty: State = ShoppingCart.State.empty

  def eventSourcedFactory: String => EventSourcedStateful[Any, State, Command, Event] =
    persistenceId => ShoppingCart(persistenceId)

  def messageHandler(
    message: Message,
    actor: ActorRef[Command]
  ): ZIO[Sharding, Throwable, Unit] =
    message match {
      case AddItem(itemId, quantity, replier)            =>
        actor
          .?(ShoppingCart.AddItem(itemId, quantity))
          .flatMap(replier.reply)
      case RemoveItem(itemId, replier)                   =>
        actor
          .?(ShoppingCart.RemoveItem(itemId))
          .flatMap(replier.reply)
      case AdjustItemQuantity(itemId, quantity, replier) =>
        actor
          .?(ShoppingCart.AdjustItemQuantity(itemId, quantity))
          .flatMap(replier.reply)
      case Checkout(replier)                             =>
        actor
          .?(ShoppingCart.Checkout)
          .flatMap(replier.reply)
      case Get(replier)                                  =>
        actor
          .?(ShoppingCart.Get)
          .flatMap(replier.reply)
    }
}
