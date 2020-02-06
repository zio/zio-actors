package zio.actors.persistence

import java.io.File

import zio.actors.{ ActorSystem, Context, Supervisor }
import zio.IO
import zio.test.DefaultRunnableSpec
import zio.test._
import zio.test.Assertion._
import CounterUtils._
import SpecUtils._

object CounterUtils {
  sealed trait Message[+_]
  case object Reset    extends Message[Unit]
  case object Increase extends Message[Unit]
  case object Get      extends Message[Int]
  case object Stop     extends Message[Unit]

  sealed trait CounterEvent
  case object ResetEvent    extends CounterEvent
  case object IncreaseEvent extends CounterEvent
}

object SpecUtils {

  val ESCounterHandler = new EventSourcedStateful[Int, Nothing, Message, CounterEvent]("id1") {
    override def receive[A](
      state: Int,
      msg: Message[A],
      context: Context
    ): IO[Nothing, (Command[CounterEvent], A)] =
      msg match {
        case Reset    => IO.effectTotal((Command.persist(ResetEvent), ()))
        case Increase => IO.effectTotal((Command.persist(IncreaseEvent), ()))
        case Get      => IO.effectTotal((Command.ignore, state))
        case Stop =>
          context.stop
            .map(_ => (Command.ignore, ()))
            .catchAll(_ => IO.effectTotal((Command.ignore, ())))
      }

    override def sourceEvent(state: Int, event: CounterEvent): Int =
      event match {
        case ResetEvent    => 0
        case IncreaseEvent => state + 1
      }
  }

  val configFile = Some(new File("./persistence/src/test/resources/application.conf"))
}

object PersistenceSpec
    extends DefaultRunnableSpec(
      suite("PersistenceSpec")(
        suite("Basic persistence operation")(
          testM("Restarting persisted actor") {
            for {
              actorSystem <- ActorSystem("testSystem1", configFile)
              actor       <- actorSystem.make("actor1", Supervisor.none, 0, ESCounterHandler)
              _           <- actor ! Increase
              _           <- actor ! Increase
              _           <- actor ? Stop
              actor       <- actorSystem.make("actor1", Supervisor.none, 0, ESCounterHandler)
              _           <- actor ! Increase
              counter     <- actor ? Get
            } yield assert(counter, equalTo(3))
          },
          testM("Corrupt plugin config name") {
            val program = for {
              as <- ActorSystem("testSystem3", configFile)
              _  <- as.make("actor1", Supervisor.none, 0, ESCounterHandler)
            } yield ()

            assertM(
              program.run,
              fails(isSubtype[Throwable](anything)) &&
                fails(hasField[Throwable, String]("message", _.getMessage, equalTo("Invalid plugin config definition")))
            )
          }
        )
      )
    )
