package zio.actors

import scalaz.zio.{ IO, Schedule }
import scalaz.zio.clock.Clock

trait Supervisor[-E] {
  def supervise[A](io: IO[E, A], error: E): IO[Unit, A]
}

object Supervisor {
  final def none: Supervisor[Any] = retry(Schedule.never)

  final def retry[E](policy: Schedule[E, _]): Supervisor[E] =
    new Supervisor[E] {

      def supervise[A](io: IO[E, A], error: E): IO[Unit, A] =
        io.retry(policy).mapError(_ => ()).provide(Clock.Live)
    }
}
