package zio.actors

import java.util.concurrent.atomic.AtomicBoolean

import zio.actors.Actor.Stateful
import zio.{ IO, Ref, Schedule }
import zio.test._
import zio.test.Assertion._

object CounterUtils {
  sealed trait Message[+_]
  case object Reset    extends Message[Unit]
  case object Increase extends Message[Unit]
  case object Get      extends Message[Int]
}

object TickUtils {
  sealed trait Message[+_]
  case object Tick extends Message[Unit]
}

object StopUtils {
  sealed trait Msg[+_]
  case object Shutdown extends Msg[List[_]]
  case object Letter   extends Msg[Unit]
}

object ActorsSpec
    extends DefaultRunnableSpec(
      suite("Test the basic actor behavior")(
        testM("sequential message processing") {
          import CounterUtils._

          val handler = new Stateful[Int, Nothing, Message] {
            override def receive[A](
              state: Int,
              msg: Message[A],
              context: Context
            ): IO[Nothing, (Int, A)] =
              msg match {
                case Reset    => IO.effectTotal((0, ()))
                case Increase => IO.effectTotal((state + 1, ()))
                case Get      => IO.effectTotal((state, state))
              }
          }

          for {
            system <- ActorSystem("test1", remoteConfig = None)
            actor  <- system.make("actor1", Supervisor.none, 0, handler)
            _      <- actor ! Increase
            _      <- actor ! Increase
            c1     <- actor ? Get
            _      <- actor ! Reset
            c2     <- actor ? Get
          } yield assert(c1, equalTo(2)) && assert(c2, equalTo(0))
        },
        testM("error recovery by retrying") {
          import TickUtils._

          val maxRetries = 10

          def makeHandler(ref: Ref[Int]): Actor.Stateful[Unit, Throwable, Message] =
            new Stateful[Unit, Throwable, Message] {
              override def receive[A](
                state: Unit,
                msg: Message[A],
                context: Context
              ): IO[Throwable, (Unit, A)] =
                msg match {
                  case Tick =>
                    ref
                      .update(_ + 1)
                      .flatMap { v =>
                        if (v < maxRetries) IO.fail(new Exception("fail"))
                        else IO.succeed((state, state))
                      }
                }
            }

          for {
            ref      <- Ref.make(0)
            handler  = makeHandler(ref)
            schedule = Schedule.recurs(maxRetries)
            policy   = Supervisor.retry(schedule)
            system   <- ActorSystem("test2", None)
            actor    <- system.make("actor1", policy, (), handler)
            _        <- actor ? Tick
            count    <- ref.get
          } yield assert(count, equalTo(maxRetries))
        },
        testM("error recovery by fallback action") {
          import TickUtils._

          val handler = new Stateful[Unit, Throwable, Message] {
            override def receive[A](
              state: Unit,
              msg: Message[A],
              context: Context
            ): IO[Throwable, (Unit, A)] =
              msg match {
                case Tick => IO.fail(new Exception("fail"))
              }
          }

          val called   = new AtomicBoolean(false)
          val schedule = Schedule.recurs(10)
          val policy =
            Supervisor.retryOrElse[Throwable, Int](
              schedule,
              (_, _) => IO.effectTotal(called.set(true))
            )

          val program = for {
            system <- ActorSystem("test3", None)
            actor  <- system.make("actor1", policy, (), handler)
            _      <- actor ? Tick
          } yield ()

          assertM(program.run, fails(anything)).andThen(assertM(IO.effectTotal(called.get), isTrue))
        },
        testM("Stopping actors") {
          import StopUtils._

          val handler = new Stateful[Unit, Throwable, Msg] {
            override def receive[A](
              state: Unit,
              msg: Msg[A],
              context: Context
            ): IO[Throwable, (Unit, A)] =
              msg match {
                case Letter => IO.succeed(((), ()))
                case Shutdown =>
                  for {
                    dump <- context.stop
                  } yield ((), dump)
              }
          }
          for {
            system <- ActorSystem("test1", remoteConfig = None)
            actor  <- system.make("actor1", Supervisor.none, (), handler)
            _      <- actor ! Letter
            _      <- actor ! Letter
            dump   <- actor ? Shutdown
          } yield assert(
            dump,
            isSubtype[List[_]](anything) &&
              hasField[List[_], Int]("size", _.size, equalTo(0))
          )
        }
      )
    )
