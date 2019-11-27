package zio.actors

import java.net.ConnectException

import zio.actors.Actor.Stateful
import zio.{ console, random, IO }
import zio.test.DefaultRunnableSpec
import zio.test._
import zio.test.Assertion._
import zio.clock.Clock
import zio.duration._
import zio.test.environment.TestConsole
import SpecUtils._

object SpecUtils {
  sealed trait Message[+A]
  case class Str(value: String) extends Message[String]

  sealed trait MyErrorDomain extends Throwable
  case object DomainError    extends MyErrorDomain

  val handlerMessageTrait = new Stateful[Int, MyErrorDomain, Message] {
    override def receive[A](
      state: Int,
      msg: Message[A],
      context: Context
    ): IO[MyErrorDomain, (Int, A)] =
      msg match {
        case Str(value) =>
          IO.effectTotal((state + 1, value + "received plus " + state + 1))
      }
  }

  sealed trait PingPongProto[+A]
  case class Ping(sender: ActorRef[Throwable, PingPongProto])        extends PingPongProto[Unit]
  case object Pong                                                   extends PingPongProto[Unit]
  case class GameInit(recipient: ActorRef[Throwable, PingPongProto]) extends PingPongProto[Unit]

  val protoHandler = new Stateful[Unit, Throwable, PingPongProto] {
    override def receive[A](
      state: Unit,
      msg: PingPongProto[A],
      context: Context
    ): IO[Throwable, (Unit, A)] =
      msg match {
        case Ping(sender) =>
          (for {
            path <- sender.path
            _    <- console.putStrLn(s"Ping from: $path, sending pong")
            _    <- sender ! Pong
          } yield ((), ())).asInstanceOf[IO[Throwable, (Unit, A)]]

        case Pong =>
          (for {
            _ <- console.putStrLn("Received pong")
            _ <- IO.succeed(1)
          } yield ((), ())).asInstanceOf[IO[Throwable, (Unit, A)]]

        case GameInit(to) =>
          (for {
            _    <- console.putStrLn("The game starts...")
            self <- context.self[Throwable, PingPongProto]
            _    <- to ! Ping(self)
          } yield ((), ())).asInstanceOf[IO[Throwable, (Unit, A)]]
      }
  }

  sealed trait ErrorProto[+A]
  case object UnsafeMessage extends ErrorProto[String]

  val errorHandler = new Stateful[Unit, Throwable, ErrorProto] {
    override def receive[A](
      state: Unit,
      msg: ErrorProto[A],
      context: Context
    ): IO[Throwable, (Unit, A)] =
      msg match {
        case UnsafeMessage => IO.fail(new Exception("Error on remote side"))
      }
  }
}

object RemoteSpec
    extends DefaultRunnableSpec(
      suite("RemoteSpec")(
        suite("Remote communication suite")(
          testM("remote test send message") {
            for {
              portRand       <- random.nextInt
              port1          = (portRand % 1000) + 8000
              port2          = port1 + 1
              actorSystemOne <- ActorSystem("testSystemOne", Some(("127.0.0.1", port1)))
              _              <- actorSystemOne.make("actorOne", Supervisor.none, 0, handlerMessageTrait)
              actorSystemTwo <- ActorSystem("testSystemTwo", Some(("127.0.0.1", port2)))
              actorRef <- actorSystemTwo.select[MyErrorDomain, Message](
                           s"zio://testSystemOne@127.0.0.1:$port1/actorOne"
                         )
              result <- actorRef ? Str("ZIO-Actor response... ")
            } yield assert(result, equalTo("ZIO-Actor response... received plus 01"))
          },
          testM("ActorRef serialization case") {
            for {
              portRand        <- random.nextInt
              port1           = (portRand % 1000) + 8000
              port2           = port1 + 1
              actorSystemRoot <- ActorSystem("testSystemOne", Some(("127.0.0.1", port1)))
              one             <- actorSystemRoot.make("actorOne", Supervisor.none, (), protoHandler)

              actorSystem <- ActorSystem("testSystemTwo", Some(("127.0.0.1", port2)))
              _           <- actorSystem.make("actorTwo", Supervisor.none, (), protoHandler)

              remoteActor <- actorSystemRoot.select[Throwable, PingPongProto](
                              s"zio://testSystemTwo@127.0.0.1:$port2/actorTwo"
                            )

              _ <- one ! GameInit(remoteActor)

              _ <- Clock.Live.clock.sleep(2.seconds)

              outputVector <- TestConsole.output
            } yield {
              assert(outputVector.size, equalTo(3)) &&
              assert(outputVector(0), equalTo("The game starts...\n")) &&
              assert(
                outputVector(1),
                equalTo(s"Ping from: zio://testSystemOne@127.0.0.1:$port1/actorOne, sending pong\n")
              ) &&
              assert(outputVector(2), equalTo("Received pong\n"))
            }
          }
        ),
        suite("Error handling suite")(
          testM("ActorRef not found case (in local actor system)") {
            val program = for {
              portRand    <- random.nextInt
              port1       = (portRand % 1000) + 8000
              actorSystem <- ActorSystem("testSystemTwo", Some(("127.0.0.1", port1)))
              _           <- actorSystem.select[Throwable, PingPongProto](s"zio://testSystemTwo@127.0.0.1:$port1/actorTwo")
            } yield ()

            assertM(
              program.run,
              fails(isSubtype[Throwable](anything)) &&
                fails(
                  hasField[Throwable, String]("message", _.getMessage, equalTo("No such actor in local ActorSystem."))
                )
            )
          },
          testM("Remote system does not exist") {
            val program = for {
              portRand    <- random.nextInt
              port1       = (portRand % 1000) + 8000
              port2       = port1 + 2
              actorSystem <- ActorSystem("testSystemTwo", Some(("127.0.0.1", port1)))
              actorRef <- actorSystem.select[Throwable, PingPongProto](
                           s"zio://testSystemOne@127.0.0.1:$port2/actorTwo"
                         )
              _ <- actorRef ! GameInit(actorRef)
            } yield ()

            assertM(
              program.run,
              fails(isSubtype[ConnectException](anything)) &&
                fails(hasField[Throwable, String]("message", _.getMessage, equalTo("Connection refused")))
            )
          },
          testM("Remote actor does not exist") {
            val program = for {
              portRand       <- random.nextInt
              port1          = (portRand % 1000) + 8000
              port2          = port1 + 1
              actorSystemOne <- ActorSystem("testSystemOne", Some(("127.0.0.1", port1)))
              _              <- ActorSystem("testSystemTwo", Some(("127.0.0.1", port2)))
              actorRef <- actorSystemOne.select[Throwable, PingPongProto](
                           s"zio://testSystemTwo@127.0.0.1:$port2/actorTwo"
                         )
              _ <- actorRef ? GameInit(actorRef)
            } yield ()

            assertM(
              program.run,
              fails(isSubtype[Throwable](anything)) &&
                fails(hasField[Throwable, String]("message", _.getMessage, equalTo("No such remote actor")))
            )
          },
          testM("On remote side error message processing error") {
            val program = for {
              portRand       <- random.nextInt
              port1          = (portRand % 1000) + 8000
              port2          = port1 + 1
              actorSystemOne <- ActorSystem("testSystemOne", Some(("127.0.0.1", port1)))
              _              <- actorSystemOne.make("actorOne", Supervisor.none, (), errorHandler)
              actorSystemTwo <- ActorSystem("testSystemTwo", Some(("127.0.0.1", port2)))
              actorRef <- actorSystemTwo.select[Throwable, ErrorProto](
                           s"zio://testSystemOne@127.0.0.1:$port1/actorOne"
                         )
              _ <- actorRef ? UnsafeMessage
            } yield ()

            assertM(
              program.run,
              fails(isSubtype[Throwable](anything)) &&
                fails(hasField[Throwable, String]("message", _.getMessage, equalTo("Error on remote side")))
            )
          }
        )
      )
    )
