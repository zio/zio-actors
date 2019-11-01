package zio.actors

import zio.actors.Actor.Stateful
import zio.{App, IO, UIO}
import zio.console._

object Main extends App {

  sealed trait Message[+A]
  case class Str(value: String) extends Message[String]

  val handler = new Stateful[Int, Any, Message] {
    override def receive[A](state: Int, msg: Message[A], system: ActorSystem): IO[Any, (Int, A)] =
      msg match {
        case Str(value) =>
          IO((1, value + "1"))
      }
  }

  def run(args: List[String]) =
    myAppLogic2.fold(_ => 1, _ => 0)

  val myAppLogic =
    for {
      actorSystem <- ActorSystem("myActorSystem", Some("127.0.0.1", 9097))
      r <- actorSystem.createActor("firstActor", Supervisor.none, 0, handler)
      t <- r.!(Str("aa"))
      _ <- putStrLn(t)
      _ <- UIO(1).forever
    } yield ()

  val myAppLogic2 =
    for {
      _ <- zio.console.putStrLn("XDD")
      actorSystemRoot <- ActorSystem("testSystemOne", Some("127.0.0.1", 9080))
      _ <- actorSystemRoot.createActor("actorOne", Supervisor.none, 0, handler)
      actorSystem <- ActorSystem("testSystemTwo", Some("127.0.0.1", 9081))
      actorRef <- actorSystem.selectActor[Any, Message]("zio://testSystemOne@127.0.0.1:9080/actorOne")
      result <- actorRef ! Str("ZIO-Actor response... ")
      _ <- zio.console.putStrLn(result)
    } yield ()

}

