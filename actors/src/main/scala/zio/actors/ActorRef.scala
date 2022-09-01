package zio.actors

import zio._
import zio.nio.{ InetAddress, InetSocketAddress }
import zio.nio.channels.AsynchronousSocketChannel

import java.io.{ IOException, ObjectInputStream, ObjectOutputStream, ObjectStreamException }

/**
 * Reference to actor that might reside on local JVM instance or be available via remote communication
 *
 * @tparam F wrapper type constructing DSL
 */
sealed trait ActorRef[-F[+_]] extends Serializable {

  /**
   * Send a message to an actor as `ask` interaction pattern -
   * caller is blocked until the response is received
   *
   * @param fa message
   * @tparam A return type
   * @return effectful response
   */
  def ?[A](fa: F[A]): Task[A]

  /**
   * Send message to an actor as `fire-and-forget` -
   * caller is blocked until message is enqueued in stub's mailbox
   *
   * @param fa message
   * @return lifted unit
   */
  def !(fa: F[_]): Task[Unit]

  /**
   * Get referential absolute actor path
   * @return
   */
  val path: UIO[String]

  /**
   * Stops actor and all its children
   */
  val stop: Task[List[_]]

}

/* INTERNAL API */
private[actors] sealed abstract class ActorRefSerial[-F[+_]](private var actorPath: String)
    extends ActorRef[F]
    with Serializable {
  import ActorSystemUtils._

  @throws[IOException]
  protected def writeObject1(out: ObjectOutputStream): Unit =
    out.writeObject(actorPath)

  @throws[IOException]
  protected def readObject1(in: ObjectInputStream): Unit = {
    val rawActorPath = in.readObject()
    actorPath = rawActorPath.asInstanceOf[String]
  }

  @throws[ObjectStreamException]
  protected def readResolve1(): Object = {
    val remoteRef = for {
      resolved          <- resolvePath(actorPath)
      (_, addr, port, _) = resolved
      address           <- InetAddress
                             .byName(addr.value)
                             .flatMap(iAddr => InetSocketAddress.inetAddress(iAddr, port.value))
    } yield new ActorRefRemote[F](actorPath, address)

    Unsafe.unsafe { implicit runtime =>
      Runtime.default.unsafe.run(remoteRef).getOrThrowFiberFailure()
    }
  }

  override val path: UIO[String] = ZIO.succeed(actorPath)
}

private[actors] final class ActorRefLocal[-F[+_]](
  private val actorName: String,
  actor: Actor[F]
) extends ActorRefSerial[F](actorName) {
  override def ?[A](fa: F[A]): Task[A] = actor ? fa

  override def !(fa: F[_]): Task[Unit] = actor ! fa

  override val stop: Task[List[_]] = actor.stop

  @throws[IOException]
  private def writeObject(out: ObjectOutputStream): Unit =
    super.writeObject1(out)

  @throws[IOException]
  private def readObject(in: ObjectInputStream): Unit =
    super.readObject1(in)

  @throws[ObjectStreamException]
  private def readResolve(): Object =
    super.readResolve1()
}

private[actors] final class ActorRefRemote[-F[+_]](
  private val actorName: String,
  address: InetSocketAddress
) extends ActorRefSerial[F](actorName) {
  import ActorSystemUtils._

  override def ?[A](fa: F[A]): Task[A] = ZIO.scoped(sendEnvelope(Command.Ask(fa)))

  override def !(fa: F[_]): Task[Unit] = ZIO.scoped(sendEnvelope[Unit](Command.Tell(fa)))

  override val stop: Task[List[_]] = ZIO.scoped(sendEnvelope(Command.Stop))

  private def sendEnvelope[A](command: Command): ZIO[Scope, Throwable, A] =
    AsynchronousSocketChannel.open.flatMap { client =>
      for {
        _         <- client.connect(address).refineToOrDie
        actorPath <- path
        _         <- writeToWire(client, new Envelope(command, actorPath))
        response  <- readFromWire(client)
        result    <- ZIO.fromEither(response.asInstanceOf[Either[Throwable, A]])
      } yield result
    }

  @throws[IOException]
  private def writeObject(out: ObjectOutputStream): Unit =
    super.writeObject1(out)

  @throws[IOException]
  private def readObject(in: ObjectInputStream): Unit =
    super.readObject1(in)

  @throws[ObjectStreamException]
  private def readResolve(): Object =
    super.readResolve1()
}
