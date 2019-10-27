package zio.actors

import zio.actors.Actor.Stateful
import zio.actors.Main.{Message, Str}
import zio.{App, IO}
import zio.console._

object Main2 extends App {


  val handler = new Stateful[Int, Any, Message] {
    override def receive[A](state: Int, msg: Message[A]): IO[Any, (Int, A)] =
      msg match {
        case Str(value) =>
          IO((1, value + "1"))
      }
  }

  def run(args: List[String]) =
    myAppLogic.fold(_ => 1, _ => 0)

  val myAppLogic =
    for {
      actorSystem <- ActorSystem("xd", 9090)
      r <- actorSystem.createActor("name2", Supervisor.none, 0, handler)
      t <- r.!(Str("aa"))
      _ <- putStrLn(t)
      remote <- actorSystem.selectActor[Any, Message]("name")
      resut <- remote.!(Str("uuuu"))
      _ <- putStrLn(resut)
    } yield ()

}

