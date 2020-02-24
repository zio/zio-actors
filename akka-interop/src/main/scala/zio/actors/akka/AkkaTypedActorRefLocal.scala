package zio.actors.akka

import akka.actor.typed
import akka.actor.typed.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import zio.{ Task, UIO, ZIO }

/**
 *
 * Reference to proxy actor that contains an akka typed actor ref
 *
 * @tparam F wrapper type constructing DSL
 */
private[actors] final class AkkaTypedActorRefLocal[-F[+_]](actorName: String, akkaActor: typed.ActorRef[F[_]])
    extends Serializable {

  /**
   *
   * Send message to an actor as fire-and-forget
   *
   * @param fa message
   * @return lifted unit
   */
  def !(fa: F[_]): Task[Unit] =
    for {
      _ <- UIO(akkaActor ! fa)
    } yield ()

  /**
   *
   * Send a message to an actor as `ask` interaction pattern -
   * caller is blocked until the response is received
   *
   * @param fa function that send a message to a hidden akka actor created by ask pattern
   * @tparam A return type
   * @return effectful response
   */
  def ?[A](fa: typed.ActorRef[A] => F[A])(implicit timeout: Timeout, scheduler: Scheduler): Task[A] =
    for {
      value <- ZIO.fromFuture(_ => akkaActor.ask[A](hiddenAkkaRef => fa(hiddenAkkaRef)))
    } yield value

  /**
   * Get referential absolute actor path
   *
   * @return
   */
  val path: UIO[String] = UIO(actorName)

  /**
   * Stops actor
   */
  val stop: Task[List[_]] = UIO(List.empty)
}
