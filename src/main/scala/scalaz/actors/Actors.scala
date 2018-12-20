package scalaz.actors

import scalaz.zio.{ IO, Promise, Queue, Ref, Schedule }

trait Actor[+E, -F[+ _]] {
  def ![A](fa: F[A]): IO[E, A]

  val stop: IO[Nothing, Unit]
}

object Actor {
  val DefaultActorMailboxSize = 10000

  trait Stateful[S, +E, -F[+ _]] {
    def receive[A](state: S, msg: F[A]): IO[E, (S, A)]
  }

  final def stateful[S, E, F[+ _]](supervisor: Supervisor[E],
                                   mailboxSize: Int = DefaultActorMailboxSize)(initial: S)(
    stateful: Actor.Stateful[S, E, F]
  ): IO[Nothing, Actor[E, F]] = {
    type PendingMessage[E, F[_], A] = (F[A], Promise[E, A])

    def process[A](msg: PendingMessage[E, F, A], state: Ref[S]): IO[Nothing, Unit] =
      for {
        s             <- state.get
        (fa, promise) = msg
        receiver      = stateful.receive(s, fa)
        completer     = ((s: S, a: A) => state.set(s) *> promise.complete(a)).tupled
        _ <- receiver.redeem(
              e =>
                supervisor
                  .supervise(receiver, e)
                  .redeem(_ => promise.error(e), completer),
              completer
            )
      } yield ()

    for {
      state <- Ref(initial)
      queue <- Queue.bounded[PendingMessage[E, F, _]](mailboxSize)
      fiber <- (for {
                t <- queue.take
                _ <- process(t, state)
              } yield ()).forever.fork
    } yield
      new Actor[E, F] {
        override def ![A](a: F[A]): IO[E, A] =
          for {
            promise <- Promise.make[E, A]
            _       <- queue.offer((a, promise))
            value   <- promise.get
          } yield value
        // TODO: Shutdown queue
        override val stop: IO[Nothing, Unit] = fiber.interrupt *> IO.unit
      }
  }
}

trait Supervisor[-E] {
  def supervise[A](io: IO[E, A], error: E): IO[Unit, A]
}

object Supervisor {
  final def none: Supervisor[Any] = retry(Schedule.never)

  final def retry[E](policy: Schedule[E, _]): Supervisor[E] =
    new Supervisor[E] {

      def supervise[A](io: IO[E, A], error: E): IO[Unit, A] =
        io.retry(policy).leftMap(_ => ())
    }
}
