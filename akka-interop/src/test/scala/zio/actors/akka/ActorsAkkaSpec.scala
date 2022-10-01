package zio.actors.akka

import akka.actor.typed
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import zio.actors.Actor.Stateful
import zio.actors._
import zio.actors.akka.AkkaBehaviorsUtils._
import zio.test.Assertion._
import zio.test._
import zio.{ IO, Runtime, Unsafe, ZIO }

import scala.concurrent.duration._

object AkkaBehaviorsUtils {

  sealed trait TypedMessage[+A]

  case object HelloFromZio extends TypedMessage[Unit]

  case class PingFromZio(zioSenderActor: ActorRef[ZioMessage]) extends TypedMessage[Unit]

  case class PingToZio(zioReplyToActor: ActorRef[ZioMessage], msg: String) extends TypedMessage[Unit]

  sealed trait ZioMessage[+A]

  case class Ping(akkaActor: AkkaTypedActorRefLocal[TypedMessage]) extends ZioMessage[Unit]

  case class UpdateFromAkka(msg: String) extends ZioMessage[Unit]

  case class PongFromAkka(msg: String) extends ZioMessage[Unit]

  object TestBehavior {
    lazy val runtime = Runtime.default

    def apply(): Behavior[TypedMessage[_]] =
      Behaviors.receiveMessage { message =>
        message match {
          case HelloFromZio                         => ()
          case PingFromZio(zioSenderActor)          =>
            Unsafe.unsafe { implicit u =>
              runtime.unsafe.run(zioSenderActor ! PongFromAkka("Pong from Akka")).getOrThrowFiberFailure()
            }
          case PingToZio(zioReplyToActor, msgToZio) =>
            Unsafe.unsafe { implicit u =>
              runtime.unsafe.run(zioReplyToActor ! PongFromAkka(msgToZio)).getOrThrowFiberFailure()
            }
        }
        Behaviors.same
      }
  }

}

object AskUtils {
  sealed trait AskMessage[+A]
  case class PingAsk(value: Int, replyTo: typed.ActorRef[Int]) extends AskMessage[Int]

  sealed trait ZioMessage[+A]
  case class GetState(akkaActor: AkkaTypedActorRefLocal[AskMessage]) extends ZioMessage[Int]

  def PingAskDeferred(value: Int): typed.ActorRef[Int] => PingAsk =
    (hiddenRef: typed.ActorRef[Int]) => PingAsk(value, hiddenRef)

  object AskTestBehavior {

    def apply(): Behavior[AskMessage[_]] =
      Behaviors.receiveMessage { message =>
        message match {
          case PingAsk(value, replyTo) => replyTo ! value
        }
        Behaviors.same
      }
  }
}

object ActorsAkkaSpec extends ZIOSpecDefault {
  def spec =
    suite("Test the basic integration with akka typed actor behavior")(
      test("Send message from zioActor to akkaActor") {
        import AkkaBehaviorsUtils._

        val handler = new Stateful[Any, Int, ZioMessage] {
          override def receive[A](
            state: Int,
            msg: ZioMessage[A],
            context: Context
          ): ZIO[Any, Throwable, (Int, A)] =
            msg match {
              case Ping(akkaActor) => (akkaActor ! HelloFromZio).as((state, ()))
              case _               => ZIO.fail(new Exception("fail"))
            }
        }

        val program = for {
          typedActorSystem <- ZIO.attempt(typed.ActorSystem(TestBehavior(), "typedSystem"))
          system           <- ActorSystem("test1")
          zioActor         <- system.make("actor1", Supervisor.none, 0, handler)
          akkaActor        <- AkkaTypedActor.make(typedActorSystem)
          _                <- zioActor ! Ping(akkaActor)
        } yield ()
        assertZIO(program.exit)(succeeds(anything))
      },
      test("Send message from akkaActor to zioActor") {
        import AkkaBehaviorsUtils._
        val handler = new Stateful[Any, String, ZioMessage] {
          override def receive[A](
            state: String,
            msg: ZioMessage[A],
            context: Context
          ): IO[Throwable, (String, A)] =
            msg match {
              case PongFromAkka(msg) => ZIO.succeed((msg, ()))
              case _                 => ZIO.fail(new Exception("fail"))
            }
        }
        val program = for {
          typedActorSystem <- ZIO.attempt(typed.ActorSystem(TestBehavior(), "typedSystem2"))
          system           <- ActorSystem("test2")
          akkaActor        <- AkkaTypedActor.make(typedActorSystem)
          zioActor         <- system.make("actor2", Supervisor.none, "", handler)
          _                <- akkaActor ! PingToZio(zioActor, "Ping from Akka")
        } yield ()
        assertZIO(program.exit)(succeeds(anything))
      },
      test("ZioActor send message to akkaActor and then replyTo to zioActor") {
        val handler =
          new Stateful[Any, String, ZioMessage] {
            override def receive[A](
              state: String,
              msg: ZioMessage[A],
              context: Context
            ): IO[Throwable, (String, A)] =
              msg match {
                case Ping(akkaActor)   =>
                  for {
                    self <- context.self[ZioMessage]
                    _    <- akkaActor ! PingFromZio(self)
                  } yield (state, ())
                case PongFromAkka(msg) => ZIO.succeed((msg, ()))
                case _                 => ZIO.fail(new Exception("fail"))
              }
          }
        val program = for {
          typedActorSystem <- ZIO.attempt(typed.ActorSystem(TestBehavior(), "typedSystem3"))
          system           <- ActorSystem("test3")
          zioActor         <- system.make("actor3", Supervisor.none, "", handler)
          akkaActor        <- AkkaTypedActor.make(typedActorSystem)
          _                <- zioActor ! Ping(akkaActor)
        } yield ()
        assertZIO(program.exit)(succeeds(anything))
      },
      test("send ask message to akkaActor and get response") {

        import AskUtils._

        val typedActorSystem = typed.ActorSystem(AskTestBehavior(), "typedSystem")

        implicit val timeout: Timeout           = 3.seconds
        implicit val scheduler: typed.Scheduler = typedActorSystem.scheduler

        def PingAskDeferred(value: Int): typed.ActorRef[Int] => PingAsk =
          (hiddenRef: typed.ActorRef[Int]) => PingAsk(value, hiddenRef)

        for {
          akkaActor <- AkkaTypedActor.make(typedActorSystem)
          result    <- akkaActor ? PingAskDeferred(1000)
        } yield assertTrue(result == 1000)
      },
      test("send message to zioActor and ask akkaActor for the response") {

        import AskUtils._

        val typedActorSystem = typed.ActorSystem(AskTestBehavior(), "typedSystem")

        implicit val timeout: Timeout           = 3.seconds
        implicit val scheduler: typed.Scheduler = typedActorSystem.scheduler

        val handler =
          new Stateful[Any, Int, ZioMessage] {
            override def receive[A](
              state: Int,
              msg: ZioMessage[A],
              context: Context
            ): IO[Throwable, (Int, A)] =
              msg match {
                case GetState(akkaActor) =>
                  (akkaActor ? PingAskDeferred(1000)).map(newState => (newState, newState))
                case _                   => ZIO.fail(new Exception("fail"))
              }
          }
        for {
          system <- ActorSystem("test3")
          zioActor  <- system.make("actor3", Supervisor.none, 0, handler)
          akkaActor <- AkkaTypedActor.make(typedActorSystem)
          result    <- zioActor ? GetState(akkaActor)
        } yield assertTrue(result == 1000)
      }
    )
}
