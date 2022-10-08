package zio.actors.sharding

import com.devsisters.shardcake.Messenger.Replier
import com.devsisters.shardcake.Sharding
import zio.actors.ActorRef
import zio.actors.persistence.EventSourcedStateful
import zio.actors.sharding.utils.ActorFinder
import zio.actors.sharding.utils.Layers.ActorSystemZ
import zio.{ Dequeue, RIO, ZIO }

trait Behavior {

  type State

  type Command[+_]

  type Event

  def stateEmpty: State

  def eventSourcedFactory: String => EventSourcedStateful[Any, State, Command, Event]
}

object Behavior {

  case class Message[A, Command[_]](
    command: Command[A],
    replier: Replier[A]
  )

  def create(b: Behavior)(
    entityId: String,
    messages: Dequeue[Behavior.Message[_, b.Command]]
  ): RIO[Sharding with ActorSystemZ, Nothing] =
    ZIO.logInfo(s"Started entity $entityId") *>
      messages.take.flatMap(handleMessage(b)(entityId, _)).forever

  private def handleMessage[A](b: Behavior)(
    entityId: String,
    message: Behavior.Message[A, b.Command]
  ): RIO[Sharding with ActorSystemZ, Unit] =
    ActorFinder
      .ref[b.State, b.Command, b.Event](
        entityId,
        b.stateEmpty,
        b.eventSourcedFactory
      )
      .flatMap(messageHandler[A](b)(message, _))

  private def messageHandler[A](b: Behavior)(
    message: Behavior.Message[A, b.Command],
    actor: ActorRef[b.Command]
  ): ZIO[Sharding, Throwable, Unit] =
    actor
      .?(message.command)
      .flatMap(message.replier.reply)

}
