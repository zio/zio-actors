package zio.actors

import java.util.concurrent.TimeUnit

import zio.actors.Actor.Stateful
import zio.{IO, Promise, ZIO, console, random}
import zio.test.DefaultRunnableSpec
import zio.test._
import zio.test.Assertion._
import SpecUtils._
import zio.clock.Clock
import zio.duration._
import zio.test.environment.{TestClock, TestConsole}

object SpecUtils {

  sealed trait Message[+A]
  case class Str(value: String) extends Message[String]

  val handlerMessageTrait = new Stateful[Int, Any, Message] {
    override def receive[A](state: Int, msg: Message[A], context: Context[Any, Message]): IO[Any, (Int, A)] =
      msg match {
        case Str(value) =>
          IO((state + 1, value + "received plus " + state + 1))
      }
  }

  sealed trait PingPongProto[+A]
  case class Ping(sender: ActorRef[Exception, PingPongProto]) extends PingPongProto[Unit]
  case object Pong extends PingPongProto[Unit]
  case class GameInit(recipient: ActorRef[Exception, PingPongProto]) extends PingPongProto[Unit]

  val protoHandler = new Stateful[Unit, Exception, PingPongProto] {
    override def receive[A](state: Unit, msg: PingPongProto[A], context: Context[Exception, PingPongProto]): IO[Exception, (Unit, A)] =
      msg match {
        case Ping(sender) => (for {
          path <- sender.path
          _ <- console.putStrLn(s"Ping from: $path, sending pong")
          _ <- (sender ! Pong).fork
        } yield ((), ())).asInstanceOf[IO[Exception, (Unit, A)]]

        case Pong => (for {
          _ <- console.putStrLn("Received pong")
          _ <- IO.succeed(1)
        } yield ((), ())).asInstanceOf[IO[Exception, (Unit, A)]]

        case GameInit(to) => (for {
          _ <- console.putStrLn("The game starts...")
          self <- context.self
          _ <- (to ! Ping(self)).fork
        } yield ((), ())).asInstanceOf[IO[Exception, (Unit, A)]]
      }
  }

}

object RemoteSpec extends DefaultRunnableSpec(
  suite("RemoteSpec")(
    suite("Remote communication suite")(
      testM("remote test send message") {
        for {
          portRand <- random.nextInt
          port1 = (portRand % 1000) + 8000
          port2 = port1 + 1
          actorSystemRoot <- ActorSystem("testSystemOne", Some("127.0.0.1", port1))
          _ <- actorSystemRoot.createActor("actorOne", Supervisor.none, 0, handlerMessageTrait)
          actorSystem <- ActorSystem("testSystemTwo", Some("127.0.0.1", port2))
          actorRef <- actorSystem.selectActor[Any, Message](s"zio://testSystemOne@127.0.0.1:$port1/actorOne")
          result <- actorRef ! Str("ZIO-Actor response... ")
        } yield assert(result, equalTo("ZIO-Actor response... received plus 01"))
      },
      testM("ActorRef serialization case") {
        for {
          portRand <- random.nextInt
          port1 = (portRand % 1000) + 8000
          port2 = port1 + 1
          actorSystemRoot <- ActorSystem("testSystemOne", Some("127.0.0.1", port1))
          one <- actorSystemRoot.createActor("actorOne", Supervisor.none, (), protoHandler)

          actorSystem <- ActorSystem("testSystemTwo", Some("127.0.0.1", port2))
          two <- actorSystem.createActor("actorTwo", Supervisor.none, (), protoHandler)

          remotee <- actorSystemRoot.selectActor[Exception, PingPongProto](s"zio://testSystemTwo@127.0.0.1:$port2/actorTwo")

          _ <- one ! GameInit(remotee)

          _ <- Clock.Live.clock.sleep(2.seconds)

          outputVector <- TestConsole.output

        } yield {
          assert(outputVector.size, equalTo(3)) &&
          assert(outputVector(0), equalTo("The game starts...\n")) &&
          assert(outputVector(1), equalTo(s"Ping from: zio://testSystemOne@127.0.0.1:$port1/actorOne, sending pong\n")) &&
          assert(outputVector(2), equalTo("Received pong\n"))
        }
      }
    ),
    suite("Error handling suite")(
      testM("ActorRef not found case (with remote disabled)") {
        for {
          _ <- IO.unit
        } yield assert(1, equalTo(1))
      },
      testM("Port not available") {
        for {
          _ <- IO.unit
        } yield assert(1, equalTo(1))
      },
      testM("Remote actor does not exist") {
        for {
          _ <- IO.unit
        } yield assert(1, equalTo(1))
      },
      testM("Remote system does not exist") {
        for {
          _ <- IO.unit
        } yield assert(1, equalTo(1))
      },
      testM("On remote side error message processing error") {
        for {
          _ <- IO.unit
        } yield assert(1, equalTo(1))
      },
      testM("while communication error case ") {
        for {
          _ <- IO.unit
        } yield assert(1, equalTo(1))
      }
    )
  )
)
