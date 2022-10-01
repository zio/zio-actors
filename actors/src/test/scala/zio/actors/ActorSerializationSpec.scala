package zio.actors
import zio.Tag
import zio.test._
object ActorSerializationSpec extends ZIOSpecDefault {
  def spec =
    suite("ActorSerializationSpec")(
      test("ActorSerialization should default to Java Serialization out the box.") {
        val sut        = ActorSerialization.default
        val serializer = sut.get[String](Tag[String])
        assertTrue(serializer.isInstanceOf[ActorSerializer.JavaSerializer[String]])
      }
    )
}
