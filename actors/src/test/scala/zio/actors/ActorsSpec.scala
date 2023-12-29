package zio.actors

import java.util.concurrent.atomic.AtomicBoolean
import zio.actors.Actor.Stateful
import zio.stream.{ Stream, ZStream }
import zio.{ Chunk, IO, Ref, Schedule, Task, UIO, ZIO }
import zio.test.*
import zio.test.Assertion.*

object CounterUtils {
  sealed trait Message[+A]
  case object Reset                   extends Message[Unit]
  case object Increase                extends Message[Unit]
  case object Get                     extends Message[Int]
  case class IncreaseUpTo(upper: Int) extends Message[Stream[Nothing, Int]]
}

object TickUtils {
  sealed trait Message[+A]
  case object Tick extends Message[Unit]
}

object StopUtils {
  sealed trait Msg[+A]
  case object Letter extends Msg[Unit]
}

object ActorsSpec extends ZIOSpecDefault {
  def spec =
    suite("Test the basic actor behavior")(
      test("Sequential message processing") {
        import CounterUtils.*

        val handler: Stateful[Any, Int, Message] = new Stateful[Any, Int, Message] {
          override def receive[A](
            state: Int,
            msg: Message[A],
            context: Context
          ): UIO[(Int, A)] =
            msg match {
              case Reset               => ZIO.succeed((0, ()))
              case Increase            => ZIO.succeed((state + 1, ()))
              case Get                 => ZIO.succeed((state, state))
              case IncreaseUpTo(upper) => ZIO.succeed((upper, ZStream.fromIterable(state until upper)))
            }
        }

        for {
          system <- ActorSystem("test1")
          actor  <- system.make("actor1", Supervisor.none, 0, handler)
          _      <- actor ! Increase
          _      <- actor ! Increase
          c1     <- actor ? Get
          _      <- actor ! Reset
          c2     <- actor ? Get
          c3     <- actor ? IncreaseUpTo(20)
          vals   <- c3.runCollect
          c4     <- actor ? Get
        } yield assertTrue(c1 == 2, c2 == 0, vals == Chunk.apply(0 until 20: _*), c4 == 20)
      },
      test("Error recovery by retrying") {
        import TickUtils.*

        val maxRetries = 10

        def makeHandler(ref: Ref[Int]): Actor.Stateful[Any, Unit, Message] =
          new Stateful[Any, Unit, Message] {
            override def receive[A](
              state: Unit,
              msg: Message[A],
              context: Context
            ): Task[(Unit, A)] =
              msg match {
                case Tick =>
                  ref
                    .updateAndGet(_ + 1)
                    .flatMap { v =>
                      if (v < maxRetries) ZIO.fail(new Exception("fail"))
                      else ZIO.succeed((state, state))
                    }
              }
          }

        for {
          ref     <- Ref.make(0)
          handler  = makeHandler(ref)
          schedule = Schedule.recurs(maxRetries)
          policy   = Supervisor.retry(schedule)
          system  <- ActorSystem("test2", None)
          actor   <- system.make("actor1", policy, (), handler)
          _       <- actor ? Tick
          count   <- ref.get
        } yield assertTrue(count == maxRetries)
      },
      test("Error recovery by fallback action") {
        import TickUtils.*

        val handler = new Stateful[Any, Unit, Message] {
          override def receive[A](
            state: Unit,
            msg: Message[A],
            context: Context
          ): IO[Throwable, (Unit, A)] =
            msg match {
              case Tick => ZIO.fail(new Exception("fail"))
            }
        }

        val called   = new AtomicBoolean(false)
        val schedule = Schedule.recurs(10)
        val policy   =
          Supervisor.retryOrElse[Any, Long](
            schedule,
            (_, _) => ZIO.succeed(called.set(true))
          )

        val program = for {
          system <- ActorSystem("test3", None)
          actor  <- system.make("actor1", policy, (), handler)
          _      <- actor ? Tick
        } yield ()

        assertZIO(program.exit)(fails(anything)) && assertZIO(ZIO.succeed(called.get))(isTrue)
      },
      test("Stopping actors") {
        import StopUtils.*

        val handler = new Stateful[Any, Unit, Msg] {
          override def receive[A](
            state: Unit,
            msg: Msg[A],
            context: Context
          ): IO[Throwable, (Unit, A)] =
            msg match {
              case Letter => ZIO.succeed(((), ()))
            }
        }
        for {
          system <- ActorSystem("test1")
          actor  <- system.make("actor1", Supervisor.none, (), handler)
          _      <- actor ! Letter
          _      <- actor ? Letter
          dump   <- actor.stop
        } yield assert(dump)(
          isSubtype[Chunk[?]](anything) &&
            hasField[Chunk[?], Int]("size", _.size, equalTo(0))
        )
      },
      test("Select local actor") {
        import TickUtils.*

        val handler = new Stateful[Any, Unit, Message] {
          override def receive[A](
            state: Unit,
            msg: Message[A],
            context: Context
          ): IO[Throwable, (Unit, A)] =
            msg match {
              case Tick => ZIO.succeed(((), ()))
            }
        }
        for {
          system    <- ActorSystem("test5")
          _         <- system.make("actor1-1", Supervisor.none, (), handler)
          actor     <- system.select[Message]("zio://test5@0.0.0.0:0000/actor1-1")
          _         <- actor ! Tick
          actorPath <- actor.path
        } yield assertTrue(actorPath == "zio://test5@0.0.0.0:0000/actor1-1")
      },
      test("Local actor does not exist") {
        import TickUtils.*

        val handler = new Stateful[Any, Unit, Message] {
          override def receive[A](
            state: Unit,
            msg: Message[A],
            context: Context
          ): IO[Throwable, (Unit, A)] =
            msg match {
              case Tick => ZIO.succeed(((), ()))
            }
        }

        val program = for {
          system <- ActorSystem("test6")
          _      <- system.make("actorOne", Supervisor.none, (), handler)
          actor  <- system.select[Message]("zio://test6@0.0.0.0:0000/actorTwo")
          _      <- actor ! Tick
        } yield ()

        assertZIO(program.exit)(
          fails(isSubtype[Throwable](anything)) &&
            fails(
              hasField[Throwable, String](
                "message",
                _.getMessage,
                equalTo("No such actor /actorTwo in local ActorSystem.")
              )
            )
        )
      }
    )
}
