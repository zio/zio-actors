package zio.actors.akka

import akka.actor.typed
import zio.{ UIO, ZIO }

object AkkaTypedActor {

  /**
   * Creates proxy reference for an akka typed actor
   *
   * @param actorRef akka actorRef
   * @tparam F - actor class behavior type
   * @return reference to the created proxy actor in effect that can't fail
   */
  def make[F[+_]](actorRef: typed.ActorRef[F[_]]): UIO[AkkaTypedActorRefLocal[F]] =
    ZIO.succeed(new AkkaTypedActorRefLocal[F](actorRef.path.toString, actorRef))
}
