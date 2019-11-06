package zio.actors

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, IOException, ObjectInputStream, ObjectOutputStream, ObjectStreamException}
import java.nio.ByteBuffer

import zio.nio.{Buffer, InetAddress, InetSocketAddress, SocketAddress}
import zio.nio.channels.AsynchronousSocketChannel
import zio.{Chunk, DefaultRuntime, IO, Task, UIO}


/**
 *
 * Reference to actor that might reside on local JVM instance or be available via remote communication
 *
 * @tparam E error type
 * @tparam F wrapper type constructing DSL
 */
sealed trait ActorRef[E <: Throwable, -F[+_]] extends Serializable {

  /**
   *
   * Send a message to actor - it can perform `fire-and-forget` interaction pattern or `ask` pattern
   *
   * @param fa message
   * @tparam A return type
   * @return effectful response
   */
  def ![A](fa: F[A]): Task[A]

  /**
   * Get referential absolute actor path
   * @return
   */
  def path: UIO[String]

}



/* INTERNAL API */


private[actors] object ActorRefSerial {

  private[actors] val runtimeForResolve = new DefaultRuntime {}

}

private[actors] sealed abstract class ActorRefSerial[E <: Throwable, -F[+_]](private var actorPath: String) extends ActorRef[E, F] with Serializable {

  import ActorSystemUtils._

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

    val remoteRef = for {
      resolved <- resolvePath(actorPath)
      (_, addr, port, _) = resolved
      address <- InetAddress.byName(addr)
        .flatMap(iAddr => SocketAddress.inetSocketAddress(iAddr, port))
    } yield ActorRefRemote[E, F](actorPath, address)

    ActorRefSerial.runtimeForResolve.unsafeRun(remoteRef)

  }

  override def path: UIO[String] = UIO.effectTotal(actorPath)

}

private[actors] case class ActorRefLocal[E <: Throwable, -F[+_]](private val actorName: String, actor: Actor[E, F]) extends ActorRefSerial[E, F](actorName) {

  override def ![A](fa: F[A]): Task[A] = actor ! fa

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

private[actors] case class ActorRefRemote[E <: Throwable, -F[+_]](private val actorName: String, address: InetSocketAddress) extends ActorRefSerial[E, F](actorName) {

  override def ![A](fa: F[A]): Task[A] = for {

    stream <- IO.effect(new ByteArrayOutputStream())
    oos <- IO.effect(new ObjectOutputStream(stream))
    actorPath <- path
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
        intBuffer <- responseBuffer.asIntBuffer
        toRead <- intBuffer.get(0)

        result <- client.read(toRead)
        arr = result.toArray

        ois = new ObjectInputStream(new ByteArrayInputStream(arr)).readObject()

      } yield ois.asInstanceOf[Either[E,A]]

    }

    result <- IO.fromEither(response)

  } yield result

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
