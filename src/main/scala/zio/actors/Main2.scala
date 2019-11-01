package zio.actors

import zio.actors.Actor.Stateful
import zio.actors.Main.{Message, Str}
import zio.{App, IO}
import zio.console._

object Main2 extends App {


  val handler = new Stateful[Int, Any, Message] {
    override def receive[A](state: Int, msg: Message[A], system: ActorSystem): IO[Any, (Int, A)] =
      msg match {
        case Str(value) =>
          IO((1, value + "1"))
      }
  }

  def run(args: List[String]) =
    myAppLogic.fold(_ => 1, _ => 0)

  val myAppLogic =
    for {
      actorSystem <- ActorSystem("SecondActorSystem", Some("127.0.0.1", 9098))
      remote <- actorSystem.selectActor[Any, Message]("zio://myActorSystem@127.0.0.1:9097/firstActor")
      resut <- remote.!(Str("ZIO-Actor response... "))
      _ <- putStrLn(resut)
    } yield ()

}

