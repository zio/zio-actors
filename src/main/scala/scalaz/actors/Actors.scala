package scalaz.actors

import scalaz.zio.{ IO, Promise, Queue, Ref, Schedule }

object Actors {

  trait Actor[+E, -F[+ _]] {
    def ![A](fa: F[A]): IO[E, A]
    def stop(): IO[Nothing, Unit]
  }

  trait MessageHandler[S, +E, -F[+ _]] {
    def receive[A](state: S, msg: F[A]): IO[E, (S, A)]
  }

  trait Supervisor[E] {
    def supervise[A](io: IO[E, A], error: E): IO[E, A]
  }

  object Supervisor {

    def none: Supervisor[Nothing] = retry(Schedule.never)

    def retry[E](policy: Schedule[E, _]): Supervisor[E] =
      new Supervisor[E] {

        def supervise[A](io: IO[E, A], error: E): IO[E, A] =
          io.retry(policy)
      }
  }

  val QueueSize = 10000

  type PendingMessage[E, F[_], A] = (F[A], Promise[E, A])

  def makeActor[S, E, F[+ _]](initial: S)(
    messageHandler: MessageHandler[S, E, F]
  )(supervisor: Supervisor[E]): IO[Nothing, Actor[E, F]] = {

    def process[A](msg: PendingMessage[E, F, A], state: Ref[S]): IO[Nothing, Unit] =
      for {
        s             <- state.get
        (fa, promise) = msg
        receiver      = messageHandler.receive(s, fa)
        completer     = ((s: S, a: A) => state.set(s) *> promise.complete(a)).tupled
        _ <- receiver.redeem(
              e =>
                supervisor
                  .supervise(receiver, e)
                  .redeem(promise.error, completer),
              completer
            )
      } yield ()

    for {
      state <- Ref(initial)
      queue <- Queue.bounded[PendingMessage[E, F, _]](QueueSize)
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
        override def stop(): IO[Nothing, Unit] =
          fiber.interrupt *> IO.unit
        // TODO queue.shutdown
      }
  }
}
