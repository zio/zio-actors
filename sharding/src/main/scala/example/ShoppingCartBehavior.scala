package example

import com.devsisters.shardcake.Sharding
import example.ShoppingCartEntity._
import zio.ZIO
import zio.actors.ActorRef
import zio.actors.persistence.EventSourcedStateful
import zio.actors.sharding.Behavior

object ShoppingCartBehavior {
  val behavior: Behavior[Message] =
    new Behavior[Message] {
      type State       = ShoppingCart.State
      type Command[+A] = ShoppingCart.Command[A]
      type Event       = ShoppingCart.Event

      def stateEmpty: State = ShoppingCart.State.empty

      def eventSourcedFactory: String => EventSourcedStateful[Any, State, Command, Event] =
        persistenceId => ShoppingCart(persistenceId)

      def messageHandler[A](
        message: Message[A],
        actor: ActorRef[Command]
      ): ZIO[Sharding, Throwable, Unit] =
        actor
          .?(message.command)
          .flatMap(message.replier.reply)
    }
}
