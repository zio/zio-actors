package zio.actors

import zio.actors.Actor.Stateful
import zio.{App, IO}
import zio.console._
import SpecUtilsMain._

object SpecUtilsMain {
  sealed trait Message[+A]
  case class Str(value: String) extends Message[String]

  val handler = new Stateful[Int, Any, Message] {
    override def receive[A](state: Int, msg: Message[A], context: Context[Any, Message]): IO[Any, (Int, A)] =
      msg match {
        case Str(value) =>
          IO((state + 1, value + "received plus " + state))
      }
  }
}

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
    myAppLogic3.fold(_ => 1, _ => 0)

  val myAppLogic =
    for {
      actorSystemRoot <- ActorSystem("testSystemOne", Some("127.0.0.1", 9382))
      _ <- actorSystemRoot.createActor("actorOne", Supervisor.none, 0, handler)
      actorSystem <- ActorSystem("testSystemTwo", Some("127.0.0.1", 9383))
      actorRef <- actorSystem.selectActor[Any, Message]("zio://testSystemOne@127.0.0.1:9382/actorOne")
      result <- actorRef ! Str("ZIO-Actor response... ")
      _ <- putStrLn(result)
    } yield ()

  val myAppLogic2 =
    for {
      actorSystemRoot <- ActorSystem("testSystemOne", Some("127.0.0.1", 9083))
      actor <- actorSystemRoot.createActor("actorOne", Supervisor.none, (), protoHandler)
      actorRef <- actorSystemRoot.selectActor[Exception, PingPongProto]("zio://SecondActorSystem@127.0.0.1:9094/actorTwo")
      _ <- actor ! GameInit(actorRef)
      _ <- IO.unit.forever
    } yield ()

  val myAppLogic3 =
    for {
      actorSystemRoot <- ActorSystem("testSystemOne", Some("127.0.0.1", 9277))
      one <- actorSystemRoot.createActor("actorOne", Supervisor.none, (), protoHandler)

      actorSystem <- ActorSystem("testSystemTwo", Some("127.0.0.1", 9278))
      two <- actorSystem.createActor("actorTwo", Supervisor.none, (), protoHandler)

      remotee <- actorSystemRoot.selectActor[Exception, PingPongProto]("zio://testSystemTwo@127.0.0.1:9278/actorTwo")

      _ <- one ! GameInit(remotee)
      _ <- IO.unit.forever
    } yield ()

}
