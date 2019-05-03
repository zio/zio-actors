package zio.actors

import scalaz.zio.{ IO, Schedule, ZIO }
import scalaz.zio.clock.Clock

trait Supervisor[-E] {
  def supervise[A](io: IO[E, A], error: E): IO[Unit, A]
}

object Supervisor {
  final def none: Supervisor[Any] = retry(Schedule.never)

  final def retry[E, A](policy: Schedule[E, A]): Supervisor[E] = 
    retryOrElse(policy, (_: E, _: A) => IO.unit)

  final def retryOrElse[E, A](policy: Schedule[E, A], orElse: (E, A) => IO[E, Unit]): Supervisor[E] =
    new Supervisor[E] {
      override def supervise[A0](io: IO[E, A0], error: E): IO[Unit, A0] = 
        io.retryOrElse(policy, (e: E, a: A) => orElse(e, a) *> ZIO.fail(error)).mapError(_ => ()).provide(Clock.Live)
    }
}
