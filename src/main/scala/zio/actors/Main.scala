package zio.actors

import zio.actors.Actor.Stateful
import zio.{App, IO}
import zio.console._

object Main extends App {

  sealed trait PingPongProto[+A]
  case class Ping(sender: ActorRef[Exception, PingPongProto]) extends PingPongProto[Unit]
  case object Pong extends PingPongProto[Unit]
  case class GameInit(recipient: ActorRef[Exception, PingPongProto]) extends PingPongProto[Unit]

  val protoHandler = new Stateful[Unit, Exception, PingPongProto] {
    override def receive[A](state: Unit, msg: PingPongProto[A], context: Context[Exception, PingPongProto]): IO[Exception, (Unit, A)] =
      msg match {
        case Ping(sender) => (for {
          path <- sender.path
          _ <- putStrLn(s"Ping from: $path, sending pong")
          _ <- (sender ! Pong).fork
        } yield ((), ())).asInstanceOf[IO[Exception, (Unit, A)]]

        case Pong => (for {
          _ <- putStrLn("Received pong")
          _ <- IO.succeed(1)
        } yield ((), ())).asInstanceOf[IO[Exception, (Unit, A)]]

        case GameInit(to) => (for {
          _ <- putStrLn("The game starts...")
          self <- context.self
          _ <- (to ! Ping(self)).fork
        } yield ((), ())).asInstanceOf[IO[Exception, (Unit, A)]]
      }
  }

  def run(args: List[String]) =
    myAppLogic2.fold(_ => 1, _ => 0)

  val myAppLogic2 =
    for {
      actorSystemRoot <- ActorSystem("testSystemOne", Some("127.0.0.1", 9081))
      actor <- actorSystemRoot.createActor("actorOne", Supervisor.none, (), protoHandler)
      actorRef <- actorSystemRoot.selectActor[Exception, PingPongProto]("zio://SecondActorSystem@127.0.0.1:9092/actorTwo")
      _ <- actor ! GameInit(actorRef)
      _ <- IO.unit.forever
    } yield ()

  val myAppLogic3 =
    for {
      actorSystemRoot <- ActorSystem("testSystemOne", Some("127.0.0.1", 9177))
      one <- actorSystemRoot.createActor("actorOne", Supervisor.none, (), protoHandler)

      actorSystem <- ActorSystem("testSystemTwo", Some("127.0.0.1", 9178))
      two <- actorSystem.createActor("actorTwo", Supervisor.none, (), protoHandler)

      remotee <- actorSystemRoot.selectActor[Exception, PingPongProto]("zio://testSystemTwo@127.0.0.1:9178/actorTwo")

      _ <- one ! GameInit(remotee)
      _ <- IO.unit.forever
    } yield ()

}
