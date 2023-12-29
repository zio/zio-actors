package zio.actors

import zio.actors.Actor.PendingMessageWrapper
import zio.{ Supervisor as _, * }

object Actor {

  private[actors] type PendingMessage[F[_], A] = (F[A], Promise[Throwable, A])
  private[actors] final case class PendingMessageWrapper[F[_], A](value: PendingMessage[F, A])

  /**
   * Description of actor behavior (can act as FSM)
   *
   * @tparam R
   *   environment type
   * @tparam S
   *   state type that is updated every message digested
   * @tparam F
   *   message DSL
   */
  trait Stateful[R, S, -F[+_]] extends AbstractStateful[R, S, F] {

    /**
     * Override method triggered on message received
     *
     * @param state
     *   available for this method
     * @param msg
     *   \- message received
     * @param context
     *   \- provisions actor's self (as ActorRef) and actors' creation and selection
     * @tparam A
     *   \- domain of return entities
     * @return
     *   effectful result
     */
    def receive[A](state: S, msg: F[A], context: Context): RIO[R, (S, A)]

    /* INTERNAL API */

    final override def makeActor(
      supervisor: Supervisor[R],
      context: Context,
      optOutActorSystem: () => Task[Unit],
      mailboxSize: Int = DefaultActorMailboxSize
    )(initial: S): URIO[R, Actor[F]] = {

      def process[A](msg: PendingMessage[F, A], state: Ref[S]): URIO[R, Unit] =
        for {
          s            <- state.get
          (fa, promise) = msg
          receiver      = receive(s, fa, context)
          completer     = ((s: S, a: A) => state.set(s) *> promise.succeed(a)).tupled
          _            <- receiver.foldZIO(
                            e =>
                              supervisor
                                .supervise(receiver, e)
                                .foldZIO(_ => promise.fail(e), completer),
                            completer
                          )
        } yield ()

      for {
        state <- Ref.make(initial)
        queue <- Queue.bounded[PendingMessageWrapper[F, _]](mailboxSize)
        _     <- (for {
                   t <- queue.take
                   _ <- process(t.value, state)
                 } yield ()).forever.fork
      } yield new Actor[F](queue)(optOutActorSystem)
    }
  }
  private[actors] trait AbstractStateful[R, S, -F[+_]] {

    private[actors] def makeActor(
      supervisor: Supervisor[R],
      context: Context,
      optOutActorSystem: () => Task[Unit],
      mailboxSize: Int = DefaultActorMailboxSize
    )(initial: S): RIO[R, Actor[F]]

  }

  private[actors] val DefaultActorMailboxSize = 10000

}

private[actors] final class Actor[-Req[+_]](
  queue: Queue[PendingMessageWrapper[Req, _]]
)(optOutActorSystem: () => Task[Unit]) {
  def ?[Res](fa: Req[Res]): Task[Res] =
    for {
      promise <- Promise.make[Throwable, Res]
      _       <- queue.offer(PendingMessageWrapper((fa, promise)))
      value   <- promise.await
    } yield value

  def !(fa: Req[Any]): Task[Unit] =
    for {
      promise <- Promise.make[Throwable, Any]
      _       <- queue.offer(PendingMessageWrapper((fa, promise)))
    } yield ()

  def unsafeOp(command: Command): Task[Any] =
    command match {
      case Command.Ask(msg)  =>
        this ? msg.asInstanceOf[Req[Any]]
      case Command.Tell(msg) =>
        this ! msg.asInstanceOf[Req[Any]]
      case Command.Stop      =>
        this.stop
    }

  val stop: Task[Chunk[?]] =
    for {
      tall <- queue.takeAll
      _    <- queue.shutdown
      _    <- optOutActorSystem()
    } yield tall
}
