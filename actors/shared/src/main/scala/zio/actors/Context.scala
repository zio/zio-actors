package zio.actors

import zio.actors.Actor.Stateful
import zio.clock.Clock
import zio.{ Ref, Task, ZIO }

/**
 * Context for actor used inside Stateful which provides self actor reference and actor creation/selection API
 */
final class Context private[actors] (
  private val path: String,
  private val actorSystem: BasicActorSystem,
  private val childrenRef: Ref[Set[ActorRef[Any]]]
) {

  /**
   * Accessor for self actor reference
   *
   * @return actor reference in a task
   */
  def self[F[+_]]: Task[ActorRef[F]] = actorSystem.select(path)

  /**
   * Creates actor and registers it to dependent actor system
   *
   * @param actorName name of the actor
   * @param sup       - supervision strategy
   * @param init      - initial state
   * @param stateful  - actor's behavior description
   * @tparam S  - state type
   * @tparam F1 - DSL type
   * @return reference to the created actor in effect that can't fail
   */
  def make[R, S, F1[+_]](
    actorName: String,
    sup: Supervisor[R],
    init: S,
    stateful: Stateful[R, S, F1]
  ): ZIO[R with Clock, Throwable, ActorRef[F1]] =
    for {
      actorRef <- actorSystem.make(actorName, sup, init, stateful)
      children <- childrenRef.get
      _        <- childrenRef.set(children + actorRef.asInstanceOf[ActorRef[Any]])
    } yield actorRef

  /**
   * Looks up for actor on local actor system, and in case of its absence - delegates it to remote internal module.
   * If remote configuration was not provided for ActorSystem (so the remoting is disabled) the search will
   * fail with ActorNotFoundException.
   * Otherwise it will always create remote actor stub internally and return ActorRef as if it was found.   *
   *
   * @param path - absolute path to the actor
   * @tparam F1 - actor's DSL type
   * @return task if actor reference. Selection process might fail with "Actor not found error"
   */
  def select[F1[+_]](path: String): Task[ActorRef[F1]] =
    actorSystem.select(path)

  /* INTERNAL API */

  private[actors] def actorSystemName = actorSystem.actorSystemName

  private[actors] def actorSystemConfig = actorSystem.config

}
