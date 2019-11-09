package zio.actors

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream }
import java.nio.ByteBuffer

import zio.{ Chunk, IO, Promise, Ref, Task, UIO }
import zio.actors.Actor.Stateful
import zio.actors.ActorSystem.{ Addr, Port }
import zio.actors.ActorSystemUtils._
import zio.nio.{ Buffer, InetAddress, SocketAddress }
import zio.nio.channels.{ AsynchronousServerSocketChannel, AsynchronousSocketChannel }

/**
 *
 *  Object providing constructor for Actor System with optional remoting module.
 *
 */
object ActorSystem {

  type Addr = String
  type Port = Int

  /**
   *
   * Constructor for Actor System
   *
   * @param name - Identifier for Actor System
   * @param remoteConfig - Optional configuration for a remoting internal module.
   *                     If not provided the actor system will only handle local actors in terms of actor selection.
   *                     When provided - remote messaging and remote actor selection is possible
   * @return instantiated actor system
   */
  def apply(name: String, remoteConfig: Option[(Addr, Port)]): Task[ActorSystem] =
    for {

      initActorRefMap <- Ref.make(Map.empty[String, Any])
      actorSystem     <- IO.effect(ActorSystem(name, remoteConfig, initActorRefMap, parentActor = None))

      _ <- remoteConfig.fold[Task[Unit]](IO.unit) {
            case (addr, port) =>
              actorSystem.receiveLoop(addr, port)
          }

    } yield actorSystem

}

/**
 *
 * Context for actor used inside Stateful which provides self actor reference and actor creation/selection API
 *
 * @tparam E error type
 * @tparam F message DSL
 */
final case class Context[E <: Throwable, F[+_]](
  private val path: String,
  private val actorSystem: ActorSystem
) {

  /**
   *
   * Accessor for self actor reference
   *
   * @return actor reference in a task
   */
  val self: Task[ActorRef[E, F]] = actorSystem.selectActor(path)

  /**
   *
   * Creates actor and registers it to dependent actor system
   *
   * @param actorName name of the actor
   * @param sup - supervision strategy
   * @param init - initial state
   * @param stateful - actor's behavior description
   * @tparam S - state type
   * @tparam E1 - custom error type
   * @tparam F1 - DSL type
   * @return reference to the created actor in effect that can't fail
   */
  def createActor[S, E1 <: Throwable, F1[+_]](
    actorName: String,
    sup: Supervisor[E1],
    init: S,
    stateful: Stateful[S, E1, F1]
  ): UIO[ActorRef[E1, F1]] =
    actorSystem.createActor(actorName, sup, init, stateful)

  /**
   *
   * Looks up for actor on local actor system, and in case of its absence - delegates it to remote internal module.
   * If remote configuration was not provided for ActorSystem (so the remoting is disabled) the search will
   * fail with ActorNotFoundException.
   * Otherwise it will always create remote actor stub internally and return ActorRef as if it was found.   *
   *
   * @param path - absolute path to the actor
   * @tparam E1 - actor's custom error type
   * @tparam F1 - actor's DSL type
   * @return task if actor reference. Selection process might fail with "Actor not found error"
   */
  def selectActor[E1 <: Throwable, F1[+_]](path: String): Task[ActorRef[E1, F1]] =
    actorSystem.selectActor(path)

}

/**
 *
 *  Type representing running instance of actor system provisioning actor herding,
 *  remoting and actor creation and selection.
 *
 */
final case class ActorSystem(
  private val actorSystemName: String,
  private val remoteConfig: Option[(Addr, Port)],
  private val refActorMap: Ref[Map[String, Any]],
  private val parentActor: Option[String]
) {

  /**
   *
   * Creates actor and registers it to dependent actor system
   *
   * @param actorName name of the actor
   * @param sup - supervision strategy
   * @param init - initial state
   * @param stateful - actor's behavior description
   * @tparam S - state type
   * @tparam E - custom error type
   * @tparam F - DSL type
   * @return reference to the created actor in effect that can't fail
   */
  def createActor[S, E <: Throwable, F[+_]](
    actorName: String,
    sup: Supervisor[E],
    init: S,
    stateful: Stateful[S, E, F]
  ): UIO[ActorRef[E, F]] =
    for {

      map       <- refActorMap.get
      finalName = parentActor.getOrElse("") + "/" + actorName
      path      = buildPath(actorSystemName, finalName, remoteConfig)
      actor     <- Actor.stateful[S, E, F](sup, Context(path, this.copy(parentActor = Some(finalName))))(init)(stateful)
      _         <- refActorMap.set(map + (finalName -> actor))

    } yield ActorRefLocal[E, F](path, actor)

  /**
   *
   * Looks up for actor on local actor system, and in case of its absence - delegates it to remote internal module.
   * If remote configuration was not provided for ActorSystem (so the remoting is disabled) the search will
   * fail with ActorNotFoundException.
   * Otherwise it will always create remote actor stub internally and return ActorRef as if it was found.   *
   *
   * @param path - absolute path to the actor
   * @tparam E - actor's custom error type
   * @tparam F - actor's DSL type
   * @return task if actor reference. Selection process might fail with "Actor not found error"
   */
  def selectActor[E <: Throwable, F[+_]](path: String): Task[ActorRef[E, F]] =
    for {

      solvedPath                              <- resolvePath(path)
      (pathActSysName, addr, port, actorName) = solvedPath

      actorMap <- refActorMap.get

      actorRef <- if (pathActSysName == actorSystemName) {

                   for {
                     actorRef <- actorMap.get(actorName) match {
                                  case Some(value) =>
                                    for {
                                      actor <- IO.effectTotal(value.asInstanceOf[Actor[E, F]])
                                    } yield ActorRefLocal(path, actor)
                                  case None =>
                                    IO.fail(new Exception("No such actor in local ActorSystem."))
                                }
                   } yield actorRef

                 } else {

                   for {
                     address <- InetAddress
                                 .byName(addr)
                                 .flatMap(iAddr => SocketAddress.inetSocketAddress(iAddr, port))
                   } yield ActorRefRemote[E, F](path, address)

                 }

    } yield actorRef

  /* INTERNAL API */

  private def receiveLoop(address: Addr, port: Port): Task[Unit] =
    for {
      addr    <- InetAddress.byName(address)
      address <- SocketAddress.inetSocketAddress(addr, port)
      p       <- Promise.make[Nothing, Unit]
      _ <- AsynchronousServerSocketChannel().use { channel =>
            for {
              _ <- channel.bind(address)

              _ <- p.succeed(())

              loop = channel.accept.flatMap(_.use { worker =>
                for {
                  obj      <- readFromWire(worker)
                  envelope = obj.asInstanceOf[Envelope]
                  actorMap <- refActorMap.get

                  _ <- actorMap.get("/" + envelope.recipient.split("/").last) match {
                        case Some(value) =>
                          for {
                            actor <- IO
                                      .effect(value.asInstanceOf[Actor[Throwable, Any]])
                                      .mapError(
                                        throwable =>
                                          new Exception(s"System internal exception - ${throwable.getMessage}")
                                      )
                            response <- actor.unsafeOp(envelope.msg).either
                            _        <- writeToWire(worker, response)
                          } yield ()
                        case None =>
                          for {
                            responseError <- IO.fail(new Exception("No such remote actor")).either
                            _             <- writeToWire(worker, responseError)
                          } yield ()
                      }

                } yield ()
              })

              _ <- loop.forever

            } yield ()
          }.fork
      _ <- p.await

    } yield ()

}

/* INTERNAL API */

private[actors] object ActorSystemUtils {

  private val regex = "^(?:zio:\\/\\/)(\\w+)[@](\\d+\\.\\d+\\.\\d+\\.\\d+)[:](\\d+)[/]([\\w|\\/]+)$".r

  def resolvePath(path: String): Task[(String, Addr, Port, String)] =
    regex.findFirstMatchIn(path) match {
      case Some(value) if value.groupCount == 4 =>
        val actorSystemName = value.group(1)
        val address         = value.group(2)
        val port            = value.group(3).toInt
        val actorName       = "/" + value.group(4)
        IO.succeed((actorSystemName, address, port, actorName))
      case None =>
        IO.fail(
          new Exception(
            "Invalid path provided. The pattern is zio://YOUR_ACTOR_SYSTEM_NAME@ADDRES:PORT/RELATIVE_ACTOR_PATH"
          )
        )
    }

  def buildPath(actorSystemName: String, actorPath: String, remoteConfig: Option[(Addr, Port)]): String =
    s"zio://$actorSystemName@${remoteConfig.map({ case (addr, port) => addr + ":" + port }).getOrElse("local")}$actorPath"

  def readFromWire(socket: AsynchronousSocketChannel): Task[Any] =
    for {
      size      <- socket.read(4)
      buffer    <- Buffer.byte(size)
      intBuffer <- buffer.asIntBuffer
      toRead    <- intBuffer.get(0)
      content   <- socket.read(toRead)
      arr       = content.toArray
      obj       = new ObjectInputStream(new ByteArrayInputStream(arr)).readObject()
    } yield obj

  def writeToWire(socket: AsynchronousSocketChannel, obj: Any): Task[Unit] =
    for {
      stream <- IO.effect(new ByteArrayOutputStream())
      oos    <- UIO.effectTotal(new ObjectOutputStream(stream))
      _      = oos.writeObject(obj)
      _      = oos.close()
      bytes  = stream.toByteArray
      _      <- socket.write(Chunk.fromArray(ByteBuffer.allocate(4).putInt(bytes.size).array()))
      _      <- socket.write(Chunk.fromArray(bytes))
    } yield ()

}
