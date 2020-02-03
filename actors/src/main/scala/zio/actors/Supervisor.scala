package zio.actors

import zio.{ IO, Schedule, ZIO }

private[actors] trait Supervisor[-R, -E] {
  def supervise[R0 <: R, A](zio: ZIO[R0, E, A], error: E): ZIO[R0, Unit, A]
}

/**
 *  Supervising policies
 */
object Supervisor {

  final def none: Supervisor[Any, Any] = retry(Schedule.once)

  final def retry[R, E, A](policy: Schedule[R, E, A]): Supervisor[R, E] =
    retryOrElse(policy, (_: E, _: A) => IO.unit)

  final def retryOrElse[R, E, A](
    policy: Schedule[R, E, A],
    orElse: (E, A) => ZIO[R, E, Unit]
  ): Supervisor[R, E] =
    new Supervisor[R, E] {
      override def supervise[R0 <: R, A0](zio: ZIO[R0, E, A0], error: E): ZIO[R0, Unit, A0] =
        zio
          .retryOrElse(policy, (e: E, a: A) => orElse(e, a) *> ZIO.fail(error))
          .mapError(_ => ())
    }
}
