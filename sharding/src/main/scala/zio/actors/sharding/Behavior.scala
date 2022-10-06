package zio.actors.sharding

import com.devsisters.shardcake.Sharding
import zio.actors.ActorRef
import zio.actors.persistence.EventSourcedStateful
import zio.actors.sharding.Layers.ActorSystemZ
import zio.{ Dequeue, RIO, ZIO }

trait Behavior[BehaviorMessage] {
  type State
  type Command[+_]
  type Event

  val stateEmpty: State
  def eventSourcedFactory: String => EventSourcedStateful[Any, State, Command, Event]
  def messageHandler(
    message: BehaviorMessage,
    actor: ActorRef[Command]
  ): ZIO[Sharding, Throwable, Unit]

  def behavior(
    entityId: String,
    messages: Dequeue[BehaviorMessage]
  ): RIO[Sharding with ActorSystemZ, Nothing] =
    ZIO.logInfo(s"Started entity $entityId") *>
      messages.take.flatMap(handleMessage(entityId, _)).forever

  def handleMessage(
    entityId: String,
    message: BehaviorMessage
  ): RIO[Sharding with ActorSystemZ, Unit] =
    ActorFinder
      .ref[State, Command, Event](
        entityId,
        stateEmpty,
        eventSourcedFactory
      )
      .flatMap { actor =>
        messageHandler(message, actor)
      }
}
