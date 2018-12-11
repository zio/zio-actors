package scalaz.actors

import java.util.concurrent.atomic.AtomicInteger
import scalaz.actors.Actors.{ MessageHandler, Supervisor }
import scalaz.zio.{ IO, RTS, Schedule }
import testz.{ Harness, assert }

final class ActorsSuite extends RTS {

  def tests[A](harness: Harness[A]): A = {
    import harness._

    section(
      test("Process messages sequentially") { () =>
        sealed trait Message[+ _]
        case object Reset    extends Message[Unit]
        case object Increase extends Message[Unit]
        case object Get      extends Message[Int]

        val handler = new MessageHandler[Int, Nothing, Message] {
          override def receive[A](state: Int, msg: Message[A]): IO[Nothing, (Int, A)] =
            msg match {
              case Reset    => IO.point((0, ()))
              case Increase => IO.point((state + 1, ()))
              case Get      => IO.point((state, state))
            }
        }

        val io = for {
          actor <- Actors.makeActor(0)(handler)(Supervisor.none)
          _     <- actor ! Increase
          _     <- actor ! Increase
          c1    <- actor ! Get
          _     <- actor ! Reset
          c2    <- actor ! Get
        } yield ((c1, c2))

        val (c1, c2) = unsafeRun(io)
        assert(c1 == 2 && c2 == 0)
      },
      test("Propagate errors to supervisor") { () =>
        sealed trait Message[+ _]
        case object Tick extends Message[Unit]

        val failures = new AtomicInteger(0)

        val handler = new MessageHandler[Unit, String, Message] {
          override def receive[A](state: Unit, msg: Message[A]): IO[String, (Unit, A)] =
            msg match {
              case Tick => IO.point(failures.incrementAndGet()) *> IO.fail("failure")
            }
        }

        val maxRetries = 10

        val io = for {
          actor <- Actors.makeActor(())(handler)(Supervisor.retry(Schedule.recurs(maxRetries)))
          _     <- actor ! Tick
        } yield (())

        val result = unsafeRunSync(io.redeem(_ => IO.unit, _ => IO.unit))

        assert(result.succeeded == true && failures.get == maxRetries + 1)
      }
    )
  }
}
