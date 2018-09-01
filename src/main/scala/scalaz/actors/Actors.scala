package scalaz.actors

import scalaz.zio.{ IO, Promise, Queue, Ref, Schedule }

object Actors {

  case class Actor[+E, -A, +B](run: A => IO[E, B])(runStop: => IO[Nothing, Unit]) {
    def !(a: A): IO[E, B] = run(a)

    def stop(): IO[Nothing, Unit] = runStop
  }

  trait Supervisor[E] {
    def supervise[A](io: IO[E, A], error: E): IO[E, A]
  }

  object Supervisor {

    def retry[E](policy: Schedule[E, _]): Supervisor[E] =
      new Supervisor[E] {

        def supervise[A](io: IO[E, A], error: E): IO[E, A] =
          io.retry(policy)
      }

    //    def log[E]: Supervisor[E] = new Supervisor[E]{
    //      def supervise[A](io: IO[E, A], error: E): IO[E, A] = ???
    //    }
  }

  val QueueSize = 1000

  /**
   * Makes an actor that starts with state `S`, and with each input
   * message `A`, produces an output message `B` and a new state `S`.
   */
  def makeActor[S, E, A, B](
    supervisor: Supervisor[E]
  )(s: S)(receive: (S, A) => IO[E, (S, B)]): IO[Nothing, Actor[E, A, B]] =
    for {
      state <- Ref(s)
      queue <- Queue.bounded[(A, Promise[E, B])](QueueSize)
      fiber <- (for {
                t            <- queue.take
                s            <- state.get
                (a, promise) = t
                receiver     = receive(s, a)
                completer    = ((s: S, b: B) => state.set(s) *> promise.complete(b)).tupled
                _ <- receiver.redeem(
                      e =>
                        supervisor
                          .supervise(receiver, e)
                          .redeem(promise.error, completer),
                      completer
                    )
              } yield ()).forever.fork
    } yield
      Actor[E, A, B] { a: A =>
        for {
          promise <- Promise.make[E, B]
          _       <- queue.offer((a, promise))
          value   <- promise.get
        } yield value
      } {
        fiber.interrupt
      }
}
