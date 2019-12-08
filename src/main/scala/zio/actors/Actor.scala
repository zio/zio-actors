package zio.actors

import zio.{ IO, Promise, Queue, Ref, Task }

object Actor {

  /**
   *
   * Description of actor behavior (can act as FSM)
   *
   * @tparam S state type that is updated every message digested
   * @tparam E error type
   * @tparam F message DSL
   */
  trait Stateful[S, +E <: Throwable, -F[+_]] {

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
    def receive[A](state: S, msg: F[A], context: Context): IO[E, (S, A)]
  }

  /* INTERNAL API */

  private val DefaultActorMailboxSize = 10000

  private[actors] final def stateful[S, E <: Throwable, F[+_]](
    supervisor: Supervisor[E],
    context: Context,
    mailboxSize: Int = DefaultActorMailboxSize
  )(initial: S)(
    stateful: Actor.Stateful[S, E, F]
  ): IO[Nothing, Actor[E, F]] = {
    type PendingMessage[E, F[_], A] = (F[A], Promise[E, A])

    def process[A](msg: PendingMessage[E, F, A], state: Ref[S]): IO[Nothing, Unit] =
      for {
        s             <- state.get
        (fa, promise) = msg
        receiver      = stateful.receive(s, fa, context)
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
    } yield new Actor[E, F] {
      override def ?[A](a: F[A]): IO[E, A] =
        for {
          promise <- Promise.make[E, A]
          _       <- queue.offer((a, promise))
          value   <- promise.await
        } yield value

      override def !(a: F[_]): IO[E, Unit] =
        for {
          promise <- Promise.make[E, Any]
          _       <- queue.offer((a, promise))
        } yield ()

      override val stop: IO[Nothing, List[_]] =
        for {
          tall <- queue.takeAll
          _    <- queue.shutdown
        } yield tall
    }
  }
}

private[actors] sealed trait Actor[+E <: Throwable, -F[+_]] {
  def ?[A](fa: F[A]): Task[A]

  def !(fa: F[_]): Task[Unit]

  final def unsafeOp(command: Command): Task[Any] =
    command match {
      case Command.Ask(msg) =>
        this.?(msg.asInstanceOf[F[_]])
      case Command.Tell(msg) =>
        this.!(msg.asInstanceOf[F[_]])
      case Command.Stop =>
        this.stop
    }

  val stop: IO[Nothing, List[_]]
}
