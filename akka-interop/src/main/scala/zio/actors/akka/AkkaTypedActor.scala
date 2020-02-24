package zio.actors.akka

import akka.actor.typed
import zio.UIO

object AkkaTypedActor {

  /**
   * Creates proxy reference for an akka typed actor
   *
   * @param actorRef akka actorRef
   * @tparam F - actor class behavior type
   * @return reference to the created proxy actor in effect that can't fail
   */
  def make[F[+_]](actorRef: typed.ActorRef[F[_]]): UIO[AkkaTypedActorRefLocal[F]] =
    for {
      akkaActorRef <- UIO(actorRef)
    } yield new AkkaTypedActorRefLocal[F](akkaActorRef.path.toString, akkaActorRef)
}
