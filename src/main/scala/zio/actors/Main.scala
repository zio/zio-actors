package zio.actors

import zio.actors.Actor.Stateful
import zio.{App, IO, UIO}
import zio.console._

object Main extends App {

  sealed trait Message[+A]
  case class Str(value: String) extends Message[String]

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
      actorSystem <- ActorSystem("xd", 9099)
      r <- actorSystem.createActor("name", Supervisor.none, 0, handler)
      t <- r.!(Str("aa"))
      _ <- putStrLn(t)
      _ <- UIO(1).forever
    } yield ()

}
