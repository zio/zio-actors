package zio.actors

import java.util.concurrent.atomic.AtomicBoolean

import org.specs2.Specification
import zio.actors.Actor.Stateful
import zio.{ DefaultRuntime, IO, Ref, Schedule }

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
    sealed trait Message
    case object Reset    extends Message
    case object Increase extends Message

    val handler = new Stateful[Ref[Int], Nothing, Message] {
      override def receive(
        state: Ref[Int],
        msg: Message
      ): IO[Nothing, (Ref[Int], Stateful[Ref[Int], Nothing, Message])] =
        msg match {
          case Reset    => state.set(0) *> IO.effectTotal((state, this))
          case Increase => (state.get >>= (v => state.set(v + 1))) *> IO.effectTotal((state, this))
        }
    }

    unsafeRun(for {
      ref   <- Ref.make(0)
      actor <- Actor.stateful(Supervisor.none)(ref)(handler)
      _     <- actor ! Increase
      _     <- actor ! Increase
      c1    <- ref.get
      _     <- actor ! Reset
      c2    <- ref.get
    } yield (c1 must_== 2).and(c2 must_== 0))

  }

  // Error recovery by retrying
  def e2 = {
    sealed trait Message
    case object Tick extends Message

    val maxRetries = 10

    def makeHandler(ref: Ref[Int]): Actor.Stateful[Unit, String, Message] =
      new Stateful[Unit, String, Message] {
        override def receive(state: Unit, msg: Message): IO[String, (Unit, Stateful[Unit, String, Message])] =
          msg match {
            case Tick =>
              ref
                .update(_ + 1)
                .flatMap { v =>
                  if (v < maxRetries) IO.fail("fail")
                  else IO.succeed(((), this))
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
    sealed trait Message
    case object Tick extends Message

    val handler = new Stateful[Unit, String, Message] {
      override def receive(state: Unit, msg: Message): IO[String, (Unit, Stateful[Unit, String, Message])] =
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
