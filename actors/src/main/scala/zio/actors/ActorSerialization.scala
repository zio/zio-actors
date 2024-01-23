package zio.actors

import zio.Tag

final class ActorSerialization private (private val map: Map[Tag[_], ActorSerializer[_]]) {
  def get[A](tag: Tag[A]): ActorSerializer[A] =
    map.getOrElse(tag, ActorSerializer.javaSerializer[A](tag)).asInstanceOf[ActorSerializer[A]]

  def setSerializer[A](serializer: ActorSerializer[A]): ActorSerialization =
    new ActorSerialization(map + (serializer.tag -> serializer))
}

object ActorSerialization {
  val default: ActorSerialization = new ActorSerialization(Map.empty)
}
