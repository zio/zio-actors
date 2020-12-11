package zio.actors

import zio.{ Task, UIO }

/**
 * Reference to actor that might reside on local JVM instance or be available via remote communication
 *
 * @tparam F wrapper type constructing DSL
 */
trait ActorRef[-F[+_]] extends Serializable {

  /**
   * Send a message to an actor as `ask` interaction pattern -
   * caller is blocked until the response is received
   *
   * @param fa message
   * @tparam A return type
   * @return effectful response
   */
  def ?[A](fa: F[A]): Task[A]

  /**
   * Send message to an actor as `fire-and-forget` -
   * caller is blocked until message is enqueued in stub's mailbox
   *
   * @param fa message
   * @return lifted unit
   */
  def !(fa: F[_]): Task[Unit]

  /**
   * Get referential absolute actor path
   * @return
   */
  val path: UIO[String]

  /**
   * Stops actor and all its children
   */
  val stop: Task[List[_]]

}

private[actors] final class ActorRefLocal[-F[+_]](
  private val actorPath: String,
  actor: Actor[F]
) extends ActorRef[F] {
  override def ?[A](fa: F[A]): Task[A] = actor ? fa

  override def !(fa: F[_]): Task[Unit] = actor ! fa

  override val stop: Task[List[_]] = actor.stop

  override val path: UIO[String] = UIO(actorPath)
}
