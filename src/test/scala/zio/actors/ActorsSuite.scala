package zio.actors

import java.util.concurrent.atomic.AtomicBoolean

import org.specs2.Specification
import scalaz.zio.{ DefaultRuntime, IO, Ref, Schedule }
import zio.actors.Actor.Stateful

final class ActorsSuite extends Specification with DefaultRuntime {

  def is =
    "ActorSpec".title ^ s2"""
    Test the basic actor behaviour:
      sequential message processing     $e1
      error recovery by retrying        $e2
      error recovery by fallback action $e3
    """

  // Sequential message processing
  def e1 = {
    sealed trait Message[+ _]
    case object Reset    extends Message[Unit]
    case object Increase extends Message[Unit]
    case object Get      extends Message[Int]

    val handler = new Stateful[Int, Nothing, Message] {
      override def receive[A](state: Int, msg: Message[A]): IO[Nothing, (Int, A)] =
        msg match {
          case Reset    => IO.succeedLazy((0, ()))
          case Increase => IO.succeedLazy((state + 1, ()))
          case Get      => IO.succeedLazy((state, state))
        }
    }

    unsafeRun(for {
      actor <- Actor.stateful(Supervisor.none)(0)(handler)
      _     <- actor ! Increase
      _     <- actor ! Increase
      c1    <- actor ! Get
      _     <- actor ! Reset
      c2    <- actor ! Get
    } yield (c1 must_== 2).and(c2 must_== 0))

  }

  // Error recovery by retrying
  def e2 = {
    sealed trait Message[+ _]
    case object Tick extends Message[Unit]

    val maxRetries = 10

    def makeHandler(ref: Ref[Int]): Actor.Stateful[Unit, String, Message] =
      new Stateful[Unit, String, Message] {
        override def receive[A](state: Unit, msg: Message[A]): IO[String, (Unit, A)] =
          msg match {
            case Tick =>
              ref
                .update(_ + 1)
                .flatMap { v =>
                  if (v < maxRetries) IO.fail("fail")
                  else IO.succeed((state, state))
                }
          }
      }

    unsafeRun(for {
      ref      <- Ref.make(0)
      handler  = makeHandler(ref)
      schedule = Schedule.recurs(maxRetries)
      policy   = Supervisor.retry(schedule)
      actor    <- Actor.stateful(policy)(())(handler)
      _        <- actor ! Tick
      count    <- ref.get
    } yield count must_== maxRetries)
  }

  // Error recovery by fallback action
  def e3 = {
    sealed trait Message[+ _]
    case object Tick extends Message[Unit]

    val handler = new Stateful[Unit, String, Message] {
      override def receive[A](state: Unit, msg: Message[A]): IO[String, (Unit, A)] =
        msg match {
          case Tick => IO.fail("fail")
        }
    }

    val called   = new AtomicBoolean(false)
    val schedule = Schedule.recurs(10)
    val policy =
      Supervisor.retryOrElse[String, Int](
        schedule,
        (_, _) => IO.succeedLazy(called.set(true))
      )

    val program = for {
      actor <- Actor.stateful(policy)(())(handler)
      _     <- actor ! Tick
    } yield ()

    (unsafeRun(program.either) must beLeft[String]).and(called.get must_== true)
  }
}
