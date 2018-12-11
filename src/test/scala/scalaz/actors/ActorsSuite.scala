package scalaz.actors

import scalaz.actors.Actors.{ MessageHandler, Supervisor }
import scalaz.zio.{ IO, RTS }
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
        assert(1 + 1 == 2)
      }
    )
  }
}
