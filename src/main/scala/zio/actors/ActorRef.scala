package zio.actors

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, IOException, ObjectInputStream, ObjectOutputStream, ObjectStreamException}
import java.nio.ByteBuffer
import zio.nio.{Buffer, InetAddress, InetSocketAddress, SocketAddress}
import zio.nio.channels.AsynchronousSocketChannel
import zio.{Chunk, IO, UIO}

sealed trait ActorRef[+E >: Exception, -F[+_]] {

  def ![A](fa: F[A]): IO[E, A]

  def path: UIO[String]

}

sealed abstract class ActorRefSerial[+E >: Exception, -F[+_]](protected var actorPath: String) extends ActorRef[E, F] {

  @throws[IOException]
  private def writeObject(out: ObjectOutputStream): Unit = {
    println("test")
    out.writeObject(actorPath)
  }

  @throws[IOException]
  private def readObject(in: ObjectInputStream): Unit = {
    val rawActorPath = in.readObject()
    actorPath = rawActorPath.asInstanceOf[String]
  }

  @throws[ObjectStreamException]
  private def readResolve(): Object = {

    for {
      e <- InetAddress.byName(actorPath)
        .flatMap(iAddr => SocketAddress.inetSocketAddress(iAddr, 123))
    } yield ActorRefRemote[E, F](actorPath, e)



  }

  override def path: UIO[String] = UIO.effectTotal(actorPath)

  private[actors] def getPath: String = actorPath

}

case class ActorRefLocal[+E >: Exception, -F[+_]](private val actorName: String, actor: Actor[E, F]) extends ActorRefSerial[E, F](actorName) {

  override def ![A](fa: F[A]): IO[E, A] = actor ! fa

}

case class ActorRefRemote[+E >: Exception, -F[+_]](private val actorName: String, address: InetSocketAddress) extends ActorRefSerial[E, F](actorName) {

  override def ![A](fa: F[A]): IO[E, A] = (for {

    stream <- IO.effectTotal(new ByteArrayOutputStream())
    oos <- UIO.effectTotal(new ObjectOutputStream(stream))
    _ = oos.writeObject(Envelope(fa, actorPath))
    _ = oos.close()
    envelopeBytes = stream.toByteArray
    envelopeSize = envelopeBytes.size

    response <- AsynchronousSocketChannel().use { client =>

      for {
        _ <- client.connect(address)

        _ <- client.write(Chunk.fromArray(ByteBuffer.allocate(4).putInt(envelopeSize).array()))
        _ <- client.write(Chunk.fromArray(envelopeBytes))

       // _ <- zio.console.putStrLn("UUUUUUU")

        responseChunk <- client.read(4)

       // _ <- zio.console.putStrLn("UUUUU2UU")

        responseBuffer <- Buffer.byte(responseChunk)

        y <- responseBuffer.asIntBuffer

        toRead <- y.get(0)

        result <- client.read(toRead)

        arr = result.toArray

        ois = new ObjectInputStream(new ByteArrayInputStream(arr)).readObject()

      } yield ois.asInstanceOf[A]

    }

  } yield response).asInstanceOf[IO[E, A]]

}
