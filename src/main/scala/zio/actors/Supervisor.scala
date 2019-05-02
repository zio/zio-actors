package zio.actors

import scalaz.zio.{ IO, Schedule }
import scalaz.zio.clock.Clock
import scalaz.zio.ZIO

trait Supervisor[-E] {
  type A0

  def supervise(io: IO[E, A0], error: E): IO[Unit, A0]
}

object Supervisor {
  final def none: Supervisor[Any] = retry(Schedule.never)

  final def retry[E, A](policy: Schedule[E, A]): Supervisor[E] =
    retryOrElse(policy, (e: E, _: A) => ZIO.fail(e))

  final def retryOrElse[E, A](policy: Schedule[E, A], orElse: (E, A) => IO[E, A]): Supervisor[E] =
    new Supervisor[E] {
      override type A0 = A

      override def supervise(io: IO[E, A0], error: E): IO[Unit, A0] = 
        io.retryOrElse(policy, orElse).mapError(_ => ()).provide(Clock.Live)
    }
}
