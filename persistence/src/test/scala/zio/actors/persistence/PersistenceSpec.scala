package zio.actors.persistence

import java.io.File

import zio.actors.{ ActorSystem, Context, Supervisor }
import zio.UIO
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

  val ESCounterHandler = new EventSourcedStateful[Any, Int, Nothing, Message, CounterEvent]("id1") {
    override def receive[A](
      state: Int,
      msg: Message[A],
      context: Context
    ): UIO[(Command[CounterEvent], Int => A)] =
      msg match {
        case Reset    => UIO((Command.persist(ResetEvent), _ => ()))
        case Increase => UIO((Command.persist(IncreaseEvent), _ => ()))
        case Get      => UIO((Command.ignore, _ => state))
        case Stop =>
          context.stop.catchAll(_ => UIO.unit) *> UIO((Command.ignore, _ => ()))
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
