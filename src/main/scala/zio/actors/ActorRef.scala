package zio.actors

import java.io.{IOException, ObjectInputStream, ObjectOutputStream, ObjectStreamException}

import zio.nio.{InetAddress, InetSocketAddress, SocketAddress}
import zio.nio.channels.AsynchronousSocketChannel
import zio.{DefaultRuntime, IO, Task, UIO}


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

  import ActorSystemUtils._

  override def ![A](fa: F[A]): Task[A] = for {

    response <- AsynchronousSocketChannel().use { client =>
      for {
        _ <- client.connect(address)
        actorPath <- path
        _ <- writeToWire(client, Envelope(fa, actorPath))
        response <- readFromWire(client)
      } yield response.asInstanceOf[Either[E,A]]
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
