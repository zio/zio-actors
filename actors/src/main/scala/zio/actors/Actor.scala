package zio.actors

import zio.actors.Actor.PendingMessage
import zio._

object Actor {

  private[actors] type PendingMessage[E, F[_], A] = (F[A], Promise[E, A])

  /**
   *
   * Description of actor behavior (can act as FSM)
   *
   * @tparam R environment type
   * @tparam S state type that is updated every message digested
   * @tparam E error type
   * @tparam F message DSL
   */
  trait Stateful[R, S, +E <: Throwable, -F[+_]] extends AbstractStateful[R, S, E, F] {

    /**
     *
     * Override method triggered on message received
     *
     * @param state available for this method
     * @param msg - message received
     * @param context - provisions actor's self (as ActorRef) and actors' creation and selection
     * @tparam A - domain of return entities
     * @return effectful result
     */
    def receive[A](state: S, msg: F[A], context: Context): ZIO[R, E, (S, A)]

    /* INTERNAL API */

    final override def makeActor(
      supervisor: Supervisor[R, E],
      context: Context,
      optOutActorSystem: () => Task[Unit],
      mailboxSize: Int = DefaultActorMailboxSize
    )(initial: S): URIO[R, Actor[E, F]] = {

      def process[A](msg: PendingMessage[E, F, A], state: Ref[S]): URIO[R, Unit] =
        for {
          s             <- state.get
          (fa, promise) = msg
          receiver      = receive(s, fa, context)
          completer     = ((s: S, a: A) => state.set(s) *> promise.succeed(a)).tupled
          _ <- receiver.foldM(
                e =>
                  supervisor
                    .supervise(receiver, e)
                    .foldM(_ => promise.fail(e), completer),
                completer
              )
        } yield ()

      for {
        state <- Ref.make(initial)
        queue <- Queue.bounded[PendingMessage[E, F, _]](mailboxSize)
        _ <- (for {
              t <- queue.take
              _ <- process(t, state)
            } yield ()).forever.fork
      } yield new Actor[E, F](queue)(optOutActorSystem)
    }
  }

  private[actors] trait AbstractStateful[R, S, +E <: Throwable, -F[+_]] {

    private[actors] def makeActor(
      supervisor: Supervisor[R, E],
      context: Context,
      optOutActorSystem: () => Task[Unit],
      mailboxSize: Int = DefaultActorMailboxSize
    )(initial: S): RIO[R, Actor[E, F]]

  }

  private[actors] val DefaultActorMailboxSize = 10000

}

private[actors] final class Actor[+E <: Throwable, -F[+_]](
  queue: Queue[PendingMessage[E, F, _]]
)(optOutActorSystem: () => Task[Unit]) {
  def ?[A](fa: F[A]): Task[A] =
    for {
      promise <- Promise.make[E, A]
      _       <- queue.offer((fa, promise))
      value   <- promise.await
    } yield value

  def !(fa: F[_]): Task[Unit] =
    for {
      promise <- Promise.make[E, Any]
      _       <- queue.offer((fa, promise))
    } yield ()

  def unsafeOp(command: Command): Task[Any] =
    command match {
      case Command.Ask(msg) =>
        this ? msg.asInstanceOf[F[_]]
      case Command.Tell(msg) =>
        this ! msg.asInstanceOf[F[_]]
      case Command.Stop =>
        this.stop
    }

  val stop: Task[List[_]] =
    for {
      tall <- queue.takeAll
      _    <- queue.shutdown
      _    <- optOutActorSystem()
    } yield tall
}
