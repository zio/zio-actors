package zio.actors.sharding

import com.devsisters.shardcake.Sharding
import zio.actors.ActorRef
import zio.actors.persistence.EventSourcedStateful
import zio.actors.sharding.Layers.ActorSystemZ
import zio.{ Dequeue, RIO, ZIO }

trait Behavior[BehaviorMessage[_]] {
  type State
  type Command[+_]
  type Event

  def stateEmpty: State
  def eventSourcedFactory: String => EventSourcedStateful[Any, State, Command, Event]

  def messageHandler[A](
    message: BehaviorMessage[A],
    actor: ActorRef[Command]
  ): ZIO[Sharding, Throwable, Unit]
}

object Behavior {

  def create[Message[_]](b: Behavior[Message])(
    entityId: String,
    messages: Dequeue[Message[_]]
  ): RIO[Sharding with ActorSystemZ, Nothing] =
    ZIO.logInfo(s"Started entity $entityId") *>
      messages.take.flatMap(handleMessage(b)(entityId, _)).forever

  private def handleMessage[Message[_]](b: Behavior[Message])(
    entityId: String,
    message: Message[_]
  ): RIO[Sharding with ActorSystemZ, Unit] =
    ActorFinder
      .ref[b.State, b.Command, b.Event](
        entityId,
        b.stateEmpty,
        b.eventSourcedFactory
      )
      .flatMap(b.messageHandler(message, _))
}
