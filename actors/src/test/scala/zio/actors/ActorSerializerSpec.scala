package zio.actors

import zio.{ RIO, ZIO }
import zio.actors.ActorSerializerTestObjects._
import zio.test._
object ActorSerializerSpec extends ZIOSpecDefault {
  def spec = suite("ActorSerializerSpec")(javaSerializerSuite)

  def javaSerializerSuite =
    suite("JavaSerializer")(
      test("It should support serializing local ActorRefs") {
        for {
          system               <- ActorSystem("java-serializer-test")
          actor                <- system.make("test", Supervisor.none, List.empty, TestScenario1.stateful)
          bytes                <- ActorSerializer.javaSerializer[ActorRef[TestScenario1.Command]].serialize(actor)
          deserializedActorRef <- ActorSerializer.javaSerializer[ActorRef[TestScenario1.Command]].deserialize(bytes)
        } yield assertTrue(bytes != Array.emptyByteArray) && assertTrue(actor == deserializedActorRef)
      }
    )

}

object ActorSerializerTestObjects {
  object TestScenario1 {
    sealed trait Command[+A]
    case class Notification(message: String) extends Command[Unit]

    val stateful = new Actor.Stateful[Any, List[String], Command] {

      override def receive[A](state: List[String], msg: Command[A], context: Context): RIO[Any, (List[String], A)] =
        msg match {
          case Notification(message) =>
            val newState = message :: state
            ZIO.succeed((newState, ()))
        }
    }
  }
}
