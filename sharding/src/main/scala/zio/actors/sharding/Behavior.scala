package zio.actors.sharding

import com.devsisters.shardcake.Sharding
import zio.{ Dequeue, RIO, ZIO }
import zio.actors.ActorRef
import zio.actors.persistence.EventSourcedStateful

import Layers.ActorSystemZ

trait Behavior[BehaviorMessage] {
  type State
  type Message[+_]
  type Event

  val stateEmpty: State
  def eventSourcedFactory: String => EventSourcedStateful[Any, State, Message, Event]
  def messageHandler(
    message: BehaviorMessage,
    actor: ActorRef[Message]
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
      .ref[State, Message, Event](
        entityId,
        stateEmpty,
        eventSourcedFactory
      )
      .flatMap { actor =>
        messageHandler(message, actor)
      }
}
