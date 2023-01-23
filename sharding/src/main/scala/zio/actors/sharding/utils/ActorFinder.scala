package zio.actors.sharding.utils

import zio.ZIO
import zio.actors.Actor.AbstractStateful
import zio.actors.sharding.utils.Layers.ActorSystemZ
import zio.actors.{ ActorRef, Supervisor }

object ActorFinder {

  def ref[State, Message[+_]](
    entityId: String,
    stateEmpty: => State,
    handler: String => AbstractStateful[Any, State, Message],
    supervisor: Supervisor[Any] = Supervisor.none
  ): ZIO[ActorSystemZ, Throwable, ActorRef[Message]] =
    ZIO.serviceWithZIO[ActorSystemZ] { actorSystemZ =>
      actorSystemZ.system
        .select[Message](actorSystemZ.basePath + entityId)
        .orElse(
          actorSystemZ.system
            .make(
              entityId,
              supervisor,
              stateEmpty,
              handler(entityId)
            )
        )
    }

}
