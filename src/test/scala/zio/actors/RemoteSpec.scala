package zio.actors

import zio.actors.Actor.Stateful
import zio.IO
import zio.test.DefaultRunnableSpec
import zio.test._
import zio.test.Assertion._
import SpecUtils._

object SpecUtils {
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

object RemoteSpec extends DefaultRunnableSpec(
  suite("Remote suite")(
    testM("remote test") {
      for {
        actorSystemRoot <- ActorSystem("testSystemOne", Some("127.0.0.1", 9082))
        _ <- actorSystemRoot.createActor("actorOne", Supervisor.none, 0, handler)
        actorSystem <- ActorSystem("testSystemTwo", Some("127.0.0.1", 9083))
        actorRef <- actorSystem.selectActor[Any, Message]("zio://testSystemOne@127.0.0.1:9083/actorOne")
        result <- actorRef ! Str("ZIO-Actor response... ")
      } yield assert(result, equalTo("ZIO-Actor response... received plus 1"))
    }
  )
)
