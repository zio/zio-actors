package zio.actors

import zio.{ Tag, Task, ZIO }
import java.io.ObjectInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream

trait ActorSerializer[A] {
  def tag: Tag[A]
  def serialize(obj: A): Task[Array[Byte]]
  def deserialize(bytes: Array[Byte]): Task[A]
}

object ActorSerializer {
  def javaSerializer[A](implicit tag: Tag[A]): ActorSerializer[A] =
    new JavaSerializer[A](tag)

  final private[actors] class JavaSerializer[A](val tag: Tag[A]) extends ActorSerializer[A] {
    override def serialize(obj: A): Task[Array[Byte]]     =
      for {
        stream <- ZIO.succeed(new ByteArrayOutputStream())
        bytes  <- ZIO.scoped {
                    ZIO
                      .acquireRelease(ZIO.attempt(new ObjectOutputStream(stream)))(s => ZIO.succeed(s.close()))
                      .flatMap { s =>
                        ZIO.attempt(s.writeObject(obj)) *> ZIO.succeed(stream.toByteArray)
                      }
                  }
      } yield bytes
    override def deserialize(bytes: Array[Byte]): Task[A] =
      ZIO.scoped {
        for {
          stream <- ZIO
                      .acquireRelease(ZIO.attempt(new ObjectInputStream(new ByteArrayInputStream(bytes))))(s =>
                        ZIO.succeed(s.close())
                      )
          obj    <- ZIO.attempt(stream.readObject())
          _      <- ZIO.logInfo(s"Deserialized object: $obj")
        } yield obj.asInstanceOf[A]
      }
  }
}
