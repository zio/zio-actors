package zio.actors

import zio.Ref

/**
 * Context for actor used inside Stateful which provides self actor reference and actor creation/selection API
 */
final class Context private[actors] (
  private val path: String,
  private val actorSystem: ActorSystem,
  private val initChildrenRef: Ref[Set[ActorRef[Any]]]
) extends BaseContext(path, actorSystem) {

  private[actors] def childrenRef[F[+_]]: Ref[Set[ActorRef[F]]] = initChildrenRef.asInstanceOf[Ref[Set[ActorRef[F]]]]

}
