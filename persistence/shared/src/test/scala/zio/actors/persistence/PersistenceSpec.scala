package zio.actors.persistence

import java.io.File
import zio.actors.{ BasicActorSystem, Context, Supervisor }
import zio.{ Task, UIO }
import zio.test.DefaultRunnableSpec
import zio.test._
import zio.test.Assertion._
import CounterUtils._
import SpecUtils._
import com.typesafe.config.ConfigFactory
import zio.actors.persistence.config.JournalPluginClass

object CounterUtils {
  sealed trait Message[+_]
  case object Reset    extends Message[Unit]
  case object Increase extends Message[Unit]
  case object Get      extends Message[Int]

  sealed trait CounterEvent
  case object ResetEvent    extends CounterEvent
  case object IncreaseEvent extends CounterEvent
}

object SpecUtils {

  def esCounterHandler(pluginClass: String) =
    new EventSourcedStateful[Any, Int, Message, CounterEvent](
      PersistenceId("id1"),
      journalRetriever = EventSourcedStateful.retrieveJournal(JournalPluginClass(pluginClass), _)
    ) {
      override def receive[A](
        state: Int,
        msg: Message[A],
        context: Context
      ): UIO[(Command[CounterEvent], Int => A)] =
        msg match {
          case Reset    => UIO((Command.persist(ResetEvent), _ => ()))
          case Increase => UIO((Command.persist(IncreaseEvent), _ => ()))
          case Get      => UIO((Command.ignore, _ => state))
        }

      override def sourceEvent(state: Int, event: CounterEvent): Int =
        event match {
          case ResetEvent    => 0
          case IncreaseEvent => state + 1
        }
    }

  val config = ConfigFactory.load()
}

object PersistenceSpec extends DefaultRunnableSpec {
  def actorSystem(name: String): Task[BasicActorSystem] = BasicActorSystem(Some(config))
  def spec                                              =
    suite("PersistenceSpec")(
      suite("Basic persistence operation")(
        testM("Restarting persisted actor") {
          val ESCounterHandler = esCounterHandler("zio.actors.persistence.journal.InMemJournal")
          for {
            actorSystem <- actorSystem("testSystem1")
            actor       <- actorSystem.make("actor1", Supervisor.none, 0, ESCounterHandler)
            _           <- actor ! Increase
            _           <- actor ? Increase
            _           <- actor.stop
            actor       <- actorSystem.make("actor1", Supervisor.none, 0, ESCounterHandler)
            _           <- actor ! Increase
            counter     <- actor ? Get
          } yield assert(counter)(equalTo(3))
        },
        testM("Plugin with a non-existing factory class") {
          val ESCounterHandler = esCounterHandler("zio.actors.persistence.NonExistent")
          val program          = for {
            as <- actorSystem("testSystem4")
            _  <- as.make("actor1", Supervisor.none, 0, ESCounterHandler)
          } yield ()

          assertM(program.run)(
            fails(isSubtype[Throwable](anything)) &&
              fails(
                hasField[Throwable, String](
                  "message",
                  e => e.toString,
                  containsString("NonExistent")
                )
              )
          )
        },
        testM("Plugin with an incorrect factory") {
          val ESCounterHandler = esCounterHandler("zio.actors.persistence.IncorrectFactory")
          val program          = for {
            as <- actorSystem("testSystem5")
            _  <- as.make("actor1", Supervisor.none, 0, ESCounterHandler)
          } yield ()

          assertM(program.run)(
            fails(isSubtype[Throwable](anything)) &&
              fails(
                hasField[Throwable, String](
                  "message",
                  _.toString,
                  containsString("IncorrectFactory")
                )
              )
          )
        }
      )
    )
}
