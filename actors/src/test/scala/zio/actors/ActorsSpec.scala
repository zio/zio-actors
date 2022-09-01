package zio.actors

import java.util.concurrent.atomic.AtomicBoolean

import zio.actors.Actor.Stateful
import zio.stream.Stream
import zio.{ Supervisor => _, _ }
import zio.test._
import zio.test.Assertion._
import zio.stream.ZStream

object CounterUtils {
  sealed trait Message[+_]
  case object Reset                   extends Message[Unit]
  case object Increase                extends Message[Unit]
  case object Get                     extends Message[Int]
  case class IncreaseUpTo(upper: Int) extends Message[Stream[Nothing, Int]]
}

object TickUtils {
  sealed trait Message[+_]
  case object Tick extends Message[Unit]
}

object StopUtils {
  sealed trait Msg[+_]
  case object Letter extends Msg[Unit]
}

object ActorsSpec extends ZIOSpecDefault {
  def spec =
    suite("Test the basic actor behavior")(
      test("Sequential message processing") {
        import CounterUtils._

        val handler: Stateful[Any, Int, Message] = new Stateful[Any, Int, Message] {
          override def receive[A](
            state: Int,
            msg: Message[A],
            context: Context
          ): UIO[(Int, A)] =
            msg match {
              case Reset               => ZIO.succeed((0, ().asInstanceOf[A])) // must need asInstanceOf?
              case Increase            => ZIO.succeed((state + 1, ().asInstanceOf[A]))
              case Get                 => ZIO.succeed((state, state.asInstanceOf[A]))
              case IncreaseUpTo(upper) =>
                ZIO.succeed((upper, ZStream.fromIterable(state until upper).asInstanceOf[A]))
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
        } yield assert(c1)(equalTo(2)) &&
          assert(c2)(equalTo(0)) &&
          assert(vals)(equalTo(Chunk.apply(0 until 20: _*))) &&
          assert(c4)(equalTo(20))
      },
      test("Error recovery by retrying") {
        import TickUtils._

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
                      else ZIO.succeed((state, state.asInstanceOf[A]))
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
        } yield assert(count)(equalTo(maxRetries))
      },
      test("Error recovery by fallback action") {
        import TickUtils._

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

        (for {
          system <- ActorSystem("test3", None)
          actor  <- system.make("actor1", policy, (), handler)
          _      <- actor ? Tick
        } yield ()).exit.map(assert(_)(fails(anything)) && assert(called.get)(isTrue))
      },
      test("Stopping actors") {
        import StopUtils._

        val handler = new Stateful[Any, Unit, Msg] {
          override def receive[A](
            state: Unit,
            msg: Msg[A],
            context: Context
          ): IO[Throwable, (Unit, A)] =
            msg match {
              case Letter => ZIO.succeed(((), ().asInstanceOf[A]))
            }
        }
        for {
          system <- ActorSystem("test1")
          actor <- system.make("actor1", Supervisor.none, (), handler)
          _     <- actor ! Letter
          _     <- actor ? Letter
          dump  <- actor.stop
        } yield assert(dump)(
          isSubtype[List[_]](anything) &&
            hasField[List[_], Int]("size", _.size, equalTo(0))
        )
      },
      test("Select local actor") {
        import TickUtils._

        val handler = new Stateful[Any, Unit, Message] {
          override def receive[A](
            state: Unit,
            msg: Message[A],
            context: Context
          ): IO[Throwable, (Unit, A)] =
            msg match {
              case Tick => ZIO.succeed(((), ().asInstanceOf[A]))
            }
        }
        for {
          system <- ActorSystem("test5")
          _         <- system.make("actor1-1", Supervisor.none, (), handler)
          actor     <- system.select[Message]("zio://test5@0.0.0.0:0000/actor1-1")
          _         <- actor ! Tick
          actorPath <- actor.path
        } yield assert(actorPath)(equalTo("zio://test5@0.0.0.0:0000/actor1-1"))
      },
      test("Local actor does not exist") {
        import TickUtils._

        val handler = new Stateful[Any, Unit, Message] {
          override def receive[A](
            state: Unit,
            msg: Message[A],
            context: Context
          ): IO[Throwable, (Unit, A)] =
            msg match {
              case Tick => ZIO.succeed(((), ().asInstanceOf[A]))
            }
        }

        (for {
          system <- ActorSystem("test6")
          _      <- system.make("actorOne", Supervisor.none, (), handler)
          actor  <- system.select[Message]("zio://test6@0.0.0.0:0000/actorTwo")
          _      <- actor ! Tick
        } yield ()).exit.map(
          assert(_)(
            fails(isSubtype[Throwable](anything)) && fails(
              hasField[Throwable, String](
                "message",
                _.getMessage,
                equalTo("No such actor /actorTwo in local ActorSystem.")
              )
            )
          )
        )
      }
    ).provideCustomLayer(ZLayer.fromZIO(testClock))
}
