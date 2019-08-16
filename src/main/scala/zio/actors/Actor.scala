package zio.actors

import zio.{ IO, Promise, Queue, Ref }

trait Actor[+E, A] {
  def !(fa: A): IO[E, Unit]

  def stop: IO[Nothing, List[_]]
}

object Actor {
  val DefaultActorMailboxSize = 10000

  trait Stateful[S, +E, A] {
    def receive(state: S, msg: A): IO[E, (S, Stateful[S, E, A])]
  }

  final def stateful[S, E, A](
    supervisor: Supervisor[E],
    mailboxSize: Int = DefaultActorMailboxSize
  )(initial: S)(
    stateful: Actor.Stateful[S, E, A]
  ): IO[Nothing, Actor[E, A]] = {
    type PendingMessage = (A, Promise[E, Unit])

    def process(msg: PendingMessage, state: Ref[S], behavior: Ref[Stateful[S, E, A]]): IO[Nothing, Unit] =
      for {
        s             <- state.get
        b             <- behavior.get
        (fa, promise) = msg
        receiver      = b.receive(s, fa)
        completer = (
          (
            s: S,
            b: Stateful[S, E, A]
          ) =>
            for {
              _ <- state.set(s)
              _ <- behavior.set(b)
              _ <- promise.succeed(())
            } yield ()
          ).tupled
        _ <- receiver.foldM(
              e =>
                supervisor
                  .supervise(receiver, e)
                  .foldM(_ => promise.fail(e), completer),
              completer
            )
      } yield ()

    for {
      state    <- Ref.make(initial)
      behavior <- Ref.make(stateful)
      queue    <- Queue.bounded[PendingMessage](mailboxSize)
      _ <- (for {
            t <- queue.take
            _ <- process(t, state, behavior)
          } yield ()).forever.fork
    } yield new Actor[E, A] {
      override def !(a: A): IO[E, Unit] =
        for {
          promise <- Promise.make[E, Unit]
          _       <- queue.offer((a, promise))
          _       <- promise.await
        } yield ()
      override def stop: IO[Nothing, List[_]] =
        for {
          tall <- queue.takeAll
          _    <- queue.shutdown
        } yield tall
    }
  }
}
