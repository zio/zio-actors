package zio.actors.akka

import akka.actor.typed
import akka.actor.typed.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import zio.{ Task, UIO, ZIO }

/**
 * Reference to proxy actor that contains an akka typed actor ref
 *
 * @tparam F
 *   wrapper type constructing DSL
 */
final class AkkaTypedActorRefLocal[-Req] private[actors] (
  private val actorName: String,
  private val akkaActor: typed.ActorRef[Req]
) extends Serializable {

  /**
   * Send message to an actor as fire-and-forget
   *
   * @param fa
   *   message
   * @return
   *   lifted unit
   */
  def !(fa: Req): Task[Unit] = ZIO.succeed(akkaActor ! fa)

  /**
   * Send a message to an actor as `ask` interaction pattern - caller is blocked until the response is received
   *
   * @param fa
   *   function that send a message to a hidden akka actor created by ask pattern
   * @tparam A
   *   return type
   * @return
   *   effectful response
   */
  def ?[Res](fa: typed.ActorRef[Res] => Req)(implicit timeout: Timeout, scheduler: Scheduler): Task[Res] =
    ZIO.fromFuture(_ => akkaActor.ask[Res](fa))

  /**
   * Get referential absolute actor path
   *
   * @return
   */
  val path: UIO[String] = ZIO.succeed(actorName)
}
