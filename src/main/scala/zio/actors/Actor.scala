package zio.actors

import zio.{IO, Promise, Queue, Ref, UIO}

trait Actor[+E >: Throwable, -F[+_]] {
  def ![A](fa: F[A]): IO[E, A]

  def unsafeOp(a: Any): IO[E, Any] =
    this.!(a.asInstanceOf[F[_]])

  def stop: IO[Nothing, List[_]]
}

object Actor {
  val DefaultActorMailboxSize = 10000

  /**
   *
   * Description of actor behavior (can act as FSM)
   *
   * @tparam S state type that is updated every message digested
   * @tparam E error type
   * @tparam F message DSL
   */
  trait Stateful[S, E >: Throwable, F[+_]] {

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
    def receive[A](state: S, msg: F[A], context: Context[E, F]): IO[E, (S, A)]
  }

  // INTERNALS

  final def stateful[S, E >: Throwable, F[+_]](supervisor: Supervisor[E],
                                                context: Context[E, F],
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
      override def ![A](a: F[A]): IO[E, A] =
        for {
          promise <- Promise.make[E, A]
          _       <- queue.offer((a, promise))
          value   <- promise.await
        } yield value
      override def stop: IO[Nothing, List[_]] =
        for {
          tall <- queue.takeAll
          _    <- queue.shutdown
        } yield tall
    }
  }
}
