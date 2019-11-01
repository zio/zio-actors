package zio.actors

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, IOException, ObjectInputStream, ObjectOutputStream, ObjectStreamException}
import java.nio.ByteBuffer
import zio.nio.{Buffer, InetAddress, InetSocketAddress, SocketAddress}
import zio.nio.channels.AsynchronousSocketChannel
import zio.{Chunk, IO, UIO}
import zio.DefaultRuntime

sealed trait ActorRef[+E >: Exception, -F[+_]] extends Serializable {

  def ![A](fa: F[A]): IO[E, A]

  def path: UIO[String]

}

object ActorRefSerial {

  private[actors] val runtimeForResolve = new DefaultRuntime {}

}

sealed abstract class ActorRefSerial[+E >: Exception, -F[+_]](protected var actorPath: String) extends ActorRef[E, F] with Serializable {

  @throws[IOException]
  protected def writeObject1(out: ObjectOutputStream): Unit = {
    out.writeObject(actorPath)
  }

  @throws[IOException]
  protected def readObject1(in: ObjectInputStream): Unit = {
    val rawActorPath = in.readObject()
    actorPath = rawActorPath.asInstanceOf[String]
  }

  @throws[ObjectStreamException]
  protected def readResolve1(): Object = {

    val (a, b, c, d) = ActorRefSerial.runtimeForResolve.unsafeRun(ActorSystem.solvePath(actorPath))

    val remoteRef = for {
      e <- InetAddress.byName(b)
        .flatMap(iAddr => SocketAddress.inetSocketAddress(iAddr, c))
    } yield ActorRefRemote[E, F](actorPath, e)

    ActorRefSerial.runtimeForResolve.unsafeRun(remoteRef)

  }

  override def path: UIO[String] = UIO.effectTotal(actorPath)

  private[actors] def getPath: String = actorPath

}

case class ActorRefLocal[+E >: Exception, -F[+_]](private val actorName: String, actor: Actor[E, F]) extends ActorRefSerial[E, F](actorName) {

  override def ![A](fa: F[A]): IO[E, A] = actor ! fa

  @throws[IOException]
  private def writeObject(out: ObjectOutputStream): Unit = {
    super.writeObject1(out)
  }

  @throws[IOException]
  private def readObject(in: ObjectInputStream): Unit = {
    super.readObject1(in)
  }

  @throws[ObjectStreamException]
  private def readResolve(): Object = {
    super.readResolve1()
  }

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

        responseChunk <- client.read(4)

        responseBuffer <- Buffer.byte(responseChunk)

        y <- responseBuffer.asIntBuffer

        toRead <- y.get(0)

        result <- client.read(toRead)

        arr = result.toArray

        ois = new ObjectInputStream(new ByteArrayInputStream(arr)).readObject()

      } yield ois.asInstanceOf[A]

    }

  } yield response).asInstanceOf[IO[E, A]]

  @throws[IOException]
  private def writeObject(out: ObjectOutputStream): Unit = {
    super.writeObject1(out)
  }

  @throws[IOException]
  private def readObject(in: ObjectInputStream): Unit = {
    super.readObject1(in)
  }

  @throws[ObjectStreamException]
  private def readResolve(): Object = {
    super.readResolve1()
  }

}
