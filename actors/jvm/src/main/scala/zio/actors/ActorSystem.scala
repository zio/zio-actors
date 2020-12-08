package zio.actors

import zio._
import zio.actors.ActorSystemUtils._
import zio.actors.BasicActorSystem.resolvePath
import zio.actors.config.{ Addr, Port, RemoteConfig }
import zio.nio.core.channels.{ AsynchronousServerSocketChannel, AsynchronousSocketChannel }
import zio.nio.core.{ Buffer, InetAddress, SocketAddress }

import java.io._
import java.nio.ByteBuffer
import scala.io.Source

/**
 * Object providing constructor for Actor System with optional remoting module.
 */
object ActorSystem {

  /**
   * Constructor for Actor System
   *
   * @param sysName    - Identifier for Actor System
   * @param configFile - Optional configuration for a remoting internal module.
   *                     If not provided the actor system will only handle local actors in terms of actor selection.
   *                     When provided - remote messaging and remote actor selection is possible
   * @return instantiated actor system
   */
  def apply(sysName: String, configFile: Option[File] = None): Task[ActorSystem] =
    for {
      initActorRefMap <- Ref.make(Map.empty[String, Any])
      config          <- retrieveConfig(configFile)
      remoteConfig    <- retrieveRemoteConfig(sysName, config)
      actorSystem     <- IO.effect(new ActorSystem(initActorRefMap, parentActor = None, config, sysName, remoteConfig))
      _               <- IO
                           .effectTotal(remoteConfig)
                           .flatMap(_.fold[Task[Unit]](IO.unit)(c => actorSystem.receiveLoop(c.addr, c.port)))
    } yield actorSystem
}

/**
 * Type representing running instance of actor system provisioning actor herding,
 * remoting and actor creation and selection.
 */
final class ActorSystem private (
  refActorMap: Ref[Map[String, Any]],
  parentActor: Option[String],
  config: Option[String],
  override val actorSystemName: String,
  private val remoteConfig: Option[RemoteConfig]
) extends BasicActorSystem(refActorMap, parentActor, config) {

  /**
   * Looks up for actor on local actor system, and in case of its absence - delegates it to remote internal module.
   * If remote configuration was not provided for ActorSystem (so the remoting is disabled) the search will
   * fail with ActorNotFoundException.
   * Otherwise it will always create remote actor stub internally and return ActorRef as if it was found.   *
   *
   * @param path - absolute path to the actor
   * @tparam F - actor's DSL type
   * @return task if actor reference. Selection process might fail with "Actor not found error"
   */
  override def select[F[+_]](path: String): Task[ActorRef[F]] =
    for {
      solvedPath                             <- resolvePath(path)
      (pathActSysName, addr, port, actorName) = solvedPath

      actorMap <- refActorMap.get

      actorRef <- if (pathActSysName == actorSystemName)
                    createActorLocalRef(path, actorName, actorMap)
                  else
                    for {
                      address <- InetAddress
                                   .byName(addr.value)
                                   .flatMap(iAddr => SocketAddress.inetSocketAddress(iAddr, port.value))
                    } yield new ActorRefRemote[F](path, address)
    } yield actorRef

  override protected def buildPath(actorPath: String)                                     =
    s"zio://$actorSystemName@${remoteConfig.map(c => c.addr.value + ":" + c.port.value).getOrElse("0.0.0.0:0000")}$actorPath"
  override protected def newActorSystem[F[+_], S, R](finalName: String): BasicActorSystem =
    new ActorSystem(refActorMap, Some(finalName), config, actorSystemName, remoteConfig)
  private def receiveLoop(address: Addr, port: Port): Task[Unit]                          =
    for {
      addr      <- InetAddress.byName(address.value)
      address   <- SocketAddress.inetSocketAddress(addr, port.value)
      p         <- Promise.make[Nothing, Unit]
      channel   <- AsynchronousServerSocketChannel()
      loopEffect = for {
                     _ <- channel.bind(address)

                     _ <- p.succeed(())

                     loop = for {
                              worker          <- channel.accept
                              obj             <- readFromWire(worker)
                              envelope         = obj.asInstanceOf[Envelope]
                              actorMap        <- refActorMap.get
                              remoteActorPath <- resolvePath(envelope.recipient).map(_._4)
                              _               <- actorMap.get(remoteActorPath) match {
                                                   case Some(value) =>
                                                     for {
                                                       actor    <-
                                                         IO
                                                           .effect(value.asInstanceOf[Actor[Any]])
                                                           .mapError(throwable =>
                                                             new Exception(s"System internal exception - ${throwable.getMessage}")
                                                           )
                                                       response <- actor.unsafeOp(envelope.command).either
                                                       _        <- response match {
                                                                     case Right(
                                                                           stream: zio.stream.ZStream[
                                                                             Any @unchecked,
                                                                             Throwable @unchecked,
                                                                             Any @unchecked
                                                                           ]
                                                                         ) =>
                                                                       stream.foreach(writeToWire(worker, _))
                                                                     case _ => writeToWire(worker, response)
                                                                   }
                                                     } yield ()
                                                   case None        =>
                                                     for {
                                                       responseError <- IO.fail(new Exception("No such remote actor")).either
                                                       _             <- writeToWire(worker, responseError)
                                                     } yield ()
                                                 }
                            } yield ()
                     _   <- loop.forever
                   } yield ()
      _         <- loopEffect.onTermination(_ => channel.close.catchAll(_ => ZIO.unit)).fork
      _         <- p.await
    } yield ()

}

/* INTERNAL API */

private[actors] object ActorSystemUtils {
  def retrieveConfig(configFile: Option[File]): Task[Option[String]] =
    configFile.fold[Task[Option[String]]](Task.none) { file =>
      IO(Source.fromFile(file)).toManaged(f => UIO(f.close())).use(s => IO.some(s.mkString))
    }

  def retrieveRemoteConfig(sysName: String, configStr: Option[String]): Task[Option[RemoteConfig]] =
    configStr.fold[Task[Option[RemoteConfig]]](Task.none)(file => ActorsConfig.getRemoteConfig(sysName, file))

  def objFromByteArray(bytes: Array[Byte]): Task[Any] =
    Task(new ObjectInputStream(new ByteArrayInputStream(bytes))).toManaged(s => UIO(s.close())).use { s =>
      Task(s.readObject())
    }

  def readFromWire(socket: AsynchronousSocketChannel): Task[Any] =
    for {
      size      <- socket.read(4)
      buffer    <- Buffer.byte(size)
      intBuffer <- buffer.asIntBuffer
      toRead    <- intBuffer.get(0)
      content   <- socket.read(toRead)
      bytes      = content.toArray
      obj       <- objFromByteArray(bytes)
    } yield obj

  def objToByteArray(obj: Any): Task[Array[Byte]] =
    for {
      stream <- UIO(new ByteArrayOutputStream())
      bytes  <- Task(new ObjectOutputStream(stream)).toManaged(s => UIO(s.close())).use { s =>
                  Task(s.writeObject(obj)) *> UIO(stream.toByteArray)
                }
    } yield bytes

  def writeToWire(socket: AsynchronousSocketChannel, obj: Any): Task[Unit] =
    for {
      bytes <- objToByteArray(obj)
      _     <- socket.write(Chunk.fromArray(ByteBuffer.allocate(4).putInt(bytes.size).array()))
      _     <- socket.write(Chunk.fromArray(bytes))
    } yield ()
}
