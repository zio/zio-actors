package zio.actors

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import java.nio.ByteBuffer

import zio.{Chunk, IO, Promise, Ref, Task, UIO}
import zio.actors.Actor.Stateful
import zio.actors.ActorSystem.{Addr, Port}
import zio.nio.{Buffer, InetAddress, SocketAddress}
import zio.nio.channels.AsynchronousServerSocketChannel
import ActorSystem._

/**
 *
 *  Object providing constructor for Actor System with remoting module
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
  def apply(name: String, remoteConfig: Option[(Addr, Port)]): Task[ActorSystem] = for {

    initActorRefMap <- Ref.make(Map.empty[String, Any])

    actorSystem <- IO.effect(ActorSystemImpl(name, remoteConfig, initActorRefMap, parentActor = None))

    _ <- remoteConfig match {
      case Some((addr, port)) =>
        actorSystem.receiveLoop(addr, port)
      case None =>
        IO.unit
    }

  } yield actorSystem

  /* INTERNAL API */

  private[actors] def resolvePath(path: String): Task[(String, Addr, Port, String)] = {

    val regex = "^(?:zio:\\/\\/)(\\w+)[@](\\d+\\.\\d+\\.\\d+\\.\\d+)[:](\\d+)[/]([\\w|\\/]+)$".r

    regex.findFirstMatchIn(path) match {
      case Some(value) if value.groupCount == 4 =>
        val actorSystemName = value.group(1)
        val address = value.group(2)
        val port = value.group(3).toInt
        val actorName = "/" + value.group(4)
        IO.succeed((actorSystemName, address, port, actorName))
      case None =>
        IO.fail(new Exception("Invalid path provided. The pattern is zio://YOUR_ACTOR_SYSTEM_NAME@ADDRES:PORT/RELATIVE_ACTOR_PATH"))
    }

  }

  private[actors] def buildPath(actorSystemName: String, actorPath: String, remoteConfig: Option[(Addr, Port)]): String =
    s"zio://$actorSystemName@${remoteConfig.map({case (addr, port) => addr + ":" + port}).getOrElse("local")}$actorPath"

}

/**
 *
 * context for actor used inside Stateful which provides self actor reference and actor creation/selection API
 *
 * @tparam E error type
 * @tparam F message DSL
 */
trait Context[E >: Throwable, F[+_]] {

  /**
   *
   * Accessor for self actor reference
   *
   * @return actor reference
   */
  def self: Task[ActorRef[E, F]]

  /**
   *
   * Creates actor and registers it to dependent actor system
   *
   * @param name
   * @param sup
   * @param init
   * @param stateful
   * @tparam S
   * @tparam E
   * @tparam F
   * @return ActorRef to crafted actor
   */
  def createActor[S, E >: Throwable, F[+_]](name: String, sup: Supervisor[E], init: S, stateful: Stateful[S, E, F]): UIO[ActorRef[E, F]]

  /**
   *
   * Looks up for actor on local actor system, and in case of its absence - delegates it to remote internal module.
   * If remote configuration was not provided for ActorSystem (so the remoting is disabled) the search will
   * fail with ActorNotFoundException.
   * Otherwise it will always create remote actor stub internally and return ActorRef as if it was found.
   *
   * @param path - full qualified path to the actor
   * @tparam E - actor's error type
   * @tparam F - actor's message DSL type
   * @return ActorRef to created actor
   */
  def selectActor[E >: Throwable, F[+_]](path: String): Task[ActorRef[E, F]]


}

/**
 *
 *  Type representing running instance of actor system provisioning actor herding,
 *  remoting and actor creation and selection.
 *
 */
trait ActorSystem {

  /**
   *
   * @param name
   * @param sup
   * @param init
   * @param stateful
   * @tparam S
   * @tparam E
   * @tparam F
   * @return
   */
  def createActor[S, E >: Throwable, F[+_]](name: String, sup: Supervisor[E], init: S, stateful: Stateful[S, E, F]): UIO[ActorRef[E, F]]

  /**
   *
   * @param path
   * @tparam E
   * @tparam F
   * @return
   */
  def selectActor[E >: Throwable, F[+_]](path: String): Task[ActorRef[E, F]]

}

/* INTERNAL API */

case class ContextImpl[E >: Throwable, F[+_]](path: String, actorSystem: ActorSystem) extends Context[E, F] {

  override def self: Task[ActorRef[E, F]] = actorSystem.selectActor(path)

  override def createActor[S, E >: Throwable, F[+_]](name: String, sup: Supervisor[E], init: S, stateful: Stateful[S, E, F]): UIO[ActorRef[E, F]] =
    actorSystem.createActor(name, sup, init, stateful)

  override def selectActor[E >: Throwable, F[+_]](path: String): Task[ActorRef[E, F]] =
    actorSystem.selectActor(path)
}

case class ActorSystemImpl(name: String,
                           remoteConfig: Option[(Addr, Port)],
                           refActorMap: Ref[Map[String, Any]],
                           parentActor: Option[String]) extends ActorSystem {

  def receiveLoop(address: Addr, port: Port): Task[Unit] = for {
    addr <- InetAddress.byName(address)
    address <- SocketAddress.inetSocketAddress(addr, port)
    p <- Promise.make[Nothing, Unit]
    _ <- (AsynchronousServerSocketChannel().use { channel =>
      for {
        _ <- channel.bind(address)

        _ <- p.succeed(())

        loop = channel.accept.flatMap(_.use { worker =>

          for {
            size <- worker.read(4)
            buff <- Buffer.byte(size)
            d <- buff.asIntBuffer
            toRead <- d.get(0)

            envelope <- worker.read(toRead)

            arr = envelope.toArray
            ois = new ObjectInputStream(new ByteArrayInputStream(arr)).readObject()

            obj = ois.asInstanceOf[Envelope]

            map <- refActorMap.get

            _ <- map.get("/" + obj.recipient.split("/").last) match {
              case Some(value) =>

                for {
                  tried <- IO.effect(value.asInstanceOf[Actor[Any, Any]])
                    .mapError(throwable => new Exception(s"System internal exception - ${throwable.getMessage}"))
                  resp <- tried.unsafeOp(obj.msg).either
                  stream: ByteArrayOutputStream = new ByteArrayOutputStream()
                  oos <- UIO.effectTotal(new ObjectOutputStream(stream))
                  _ = oos.writeObject(resp)
                  _ = oos.close()
                  e = stream.toByteArray

                  _ <- worker.write(Chunk.fromArray(ByteBuffer.allocate(4).putInt(e.size).array()))
                  _ <- worker.write(Chunk.fromArray(e))

                } yield ()

              case None =>

                for {
                  responseError <- IO.fail(new Exception("No such remote actor")).either
                  stream: ByteArrayOutputStream = new ByteArrayOutputStream()
                  oos <- UIO.effectTotal(new ObjectOutputStream(stream))
                  _ = oos.writeObject(responseError)
                  _ = oos.close()
                  e = stream.toByteArray

                  _ <- worker.write(Chunk.fromArray(ByteBuffer.allocate(4).putInt(e.size).array()))
                  _ <- worker.write(Chunk.fromArray(e))
                } yield ()

            }

          } yield ()

        })

        _ <- loop.forever

      } yield ()
    }).fork
    _ <- p.await

  } yield ()

  def createActor[S, E >: Throwable, F[+_]](actname: String, sup: Supervisor[E], init: S, stateful: Stateful[S, E, F]): UIO[ActorRef[E, F]] = for {

    map <- refActorMap.get
    finalName = parentActor.getOrElse("") + "/" + actname
    path = buildPath(name, finalName, remoteConfig)
    actor <- Actor.stateful[S, E, F](sup, ContextImpl(path, this.copy(parentActor = Some(finalName))))(init)(stateful)
    _ <- refActorMap.set(map + (finalName -> actor))

  } yield ActorRefLocal[E, F](path, actor)

  override def selectActor[E >: Throwable, F[+_]](path: String): Task[ActorRef[E, F]] = for {

    solvedPath <- ActorSystem.resolvePath(path)
    (ac, add, port, act) = solvedPath

    d <- refActorMap.get

    actorRef <- if (ac == name) {

      for {
        actorRef <- d.get(act) match {
          case Some(value) =>
            for {
              t <- IO.effectTotal(value.asInstanceOf[Actor[E, F]])
            } yield ActorRefLocal(path, t)
          case None =>
            IO.fail(new Exception("No such actor in local ActorSystem."))
        }
      } yield actorRef

    } else {

      for {
        e <- InetAddress.byName(add)
          .flatMap(iAddr => SocketAddress.inetSocketAddress(iAddr, port))
      } yield ActorRefRemote[E, F](path, e)

    }

  } yield actorRef

}
