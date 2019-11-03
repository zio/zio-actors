package zio.actors

import zio.actors.Main.protoHandler
import zio.{App, IO}

object Main2 extends App {

  def run(args: List[String]) =
    myAppLogic.fold(_ => 1, _ => 0)

  val myAppLogic =
    for {
      actorSystem <- ActorSystem("SecondActorSystem", Some("127.0.0.1", 9094))
      _ <- actorSystem.createActor("actorTwo", Supervisor.none, (), protoHandler)
      _ <- IO.unit.forever
    } yield ()

}

