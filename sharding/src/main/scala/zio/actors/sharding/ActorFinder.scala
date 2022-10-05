package zio.actors.sharding

import zio.ZIO
import zio.actors.{ ActorRef, Supervisor }
import zio.actors.persistence.EventSourcedStateful

import Layers.ActorSystemZ

// TODO: create a more efficient method in ActorSystem
object ActorFinder {
  def ref[State, Message[+_], Event](
    entityId: String,
    stateEmpty: => State,
    handler: String => EventSourcedStateful[Any, State, Message, Event]
  ): ZIO[ActorSystemZ, Throwable, ActorRef[Message]] =
    ZIO.serviceWithZIO[ActorSystemZ] { actorSystemZ =>
      actorSystemZ.system
        .select[Message](actorSystemZ.basePath + entityId)
        .orElse(
          actorSystemZ.system
            .make(
              entityId,
              Supervisor.none,
              stateEmpty,
              handler(entityId)
            )
        )
    }
}
