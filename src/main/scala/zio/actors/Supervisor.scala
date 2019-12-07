package zio.actors

import zio.{ IO, Schedule, ZIO }

private[actors] trait Supervisor[-E] {
  def supervise[A](io: IO[E, A], error: E): IO[Unit, A]
}

/**
 *  Supervising policies
 */
object Supervisor {
  final def none: Supervisor[Any] = retry(Schedule.once)

  final def retry[E, A](policy: Schedule[Any, E, A]): Supervisor[E] =
    retryOrElse(policy, (_: E, _: A) => IO.unit)

  final def retryOrElse[E, A](
    policy: Schedule[Any, E, A],
    orElse: (E, A) => IO[E, Unit]
  ): Supervisor[E] =
    new Supervisor[E] {
      override def supervise[A0](io: IO[E, A0], error: E): IO[Unit, A0] =
        io.retryOrElse(policy, (e: E, a: A) => orElse(e, a) *> ZIO.fail(error))
          .mapError(_ => ())
    }
}
