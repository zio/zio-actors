package zio.actors

import zio.actors.Actor.AbstractStateful
import zio.actors.ActorSystemUtils.*
import zio.actors.ActorsConfig.*
import zio.nio.channels.AsynchronousServerSocketChannel
import zio.nio.{ InetAddress, InetSocketAddress }
import zio.{ Promise, RIO, Ref, Task, ZIO }

import java.io.*

/**
 * Object providing constructor for Actor System with optional remoting module.
 */
object ActorSystem {

  /**
   * Constructor for Actor System
   *
   * @param sysName
   *   \- Identifier for Actor System
   * @param configFile
   *   \- Optional configuration for a remoting internal module. If not provided the actor system will only handle local
   *   actors in terms of actor selection. When provided - remote messaging and remote actor selection is possible
   * @return
   *   instantiated actor system
   */
  def apply(sysName: String, configFile: Option[File] = None): Task[ActorSystem] =
    for {
      initActorRefMap <- Ref.make(Map.empty[String, Actor[Any]])
      config          <- retrieveConfig(configFile)
      remoteConfig    <- retrieveRemoteConfig(sysName, config)
      actorSystem     <- ZIO.attempt(new ActorSystem(sysName, config, remoteConfig, initActorRefMap, parentActor = None))
      _               <- ZIO
                           .succeed(remoteConfig)
                           .flatMap(_.fold[Task[Unit]](ZIO.unit)(c => actorSystem.receiveLoop(c.addr, c.port)))
    } yield actorSystem
}

/**
 * Type representing running instance of actor system provisioning actor herding, remoting and actor creation and
 * selection.
 */
final class ActorSystem private[actors] (
  private[actors] val actorSystemName: String,
  private[actors] val config: Option[String],
  private val remoteConfig: Option[RemoteConfig],
  private val refActorMap: Ref[Map[String, Actor[Any]]],
  private val parentActor: Option[String]
) {

  /**
   * Creates actor and registers it to dependent actor system
   *
   * @param actorName
   *   name of the actor
   * @param sup
   *   \- supervision strategy
   * @param init
   *   \- initial state
   * @param stateful
   *   \- actor's behavior description
   * @tparam S
   *   \- state type
   * @tparam F
   *   \- DSL type
   * @return
   *   reference to the created actor in effect that can't fail
   */
  def make[R, S, F[+_]](
    actorName: String,
    sup: Supervisor[R],
    init: S,
    stateful: AbstractStateful[R, S, F]
  ): RIO[R, ActorRef[F]] =
    for {
      map          <- refActorMap.get
      finalName    <- buildFinalName(parentActor.getOrElse(""), actorName)
      _            <- ZIO.fail(new Exception(s"Actor $finalName already exists")).when(map.contains(finalName))
      path          = buildPath(actorSystemName, finalName, remoteConfig)
      derivedSystem = new ActorSystem(actorSystemName, config, remoteConfig, refActorMap, Some(finalName))
      childrenSet  <- Ref.make(Set.empty[ActorRef[Any]])
      actor        <- stateful.makeActor(
                        sup,
                        new Context(path, derivedSystem, childrenSet),
                        () => dropFromActorMap(path, childrenSet)
                      )(init)
      _            <- refActorMap.set(map + (finalName -> actor.asInstanceOf[Actor[Any]]))
    } yield new ActorRefLocal[F](path, actor)

  /**
   * Looks up for actor on local actor system, and in case of its absence - delegates it to remote internal module. If
   * remote configuration was not provided for ActorSystem (so the remoting is disabled) the search will fail with
   * ActorNotFoundException. Otherwise it will always create remote actor stub internally and return ActorRef as if it
   * was found. *
   *
   * @param path
   *   \- absolute path to the actor
   * @tparam F
   *   \- actor's DSL type
   * @return
   *   task if actor reference. Selection process might fail with "Actor not found error"
   */
  def select[F[+_]](path: String): Task[ActorRef[F]] =
    for {
      solvedPath                             <- resolvePath(path)
      (pathActSysName, addr, port, actorName) = solvedPath

      actorMap <- refActorMap.get

      actorRef <- if (pathActSysName == actorSystemName)
                    for {
                      actorRef <- actorMap.get(actorName) match {
                                    case Some(value) =>
                                      for {
                                        actor <- ZIO.succeed(value.asInstanceOf[Actor[F]])
                                      } yield new ActorRefLocal(path, actor)
                                    case None        =>
                                      ZIO.fail(new Exception(s"No such actor $actorName in local ActorSystem."))
                                  }
                    } yield actorRef
                  else
                    for {
                      address <- InetAddress
                                   .byName(addr.value)
                                   .flatMap(iAddr => InetSocketAddress.inetAddress(iAddr, port.value))
                    } yield new ActorRefRemote[F](path, address)
    } yield actorRef

  /**
   * Stops all actors within this ActorSystem.
   *
   * @return
   *   all actors' unprocessed messages
   */
  def shutdown: Task[List[?]] =
    for {
      systemActors <- refActorMap.get
      actorsDump   <- ZIO.foreach(systemActors.values.toList)(_.stop)
    } yield actorsDump.flatten

  /* INTERNAL API */

  private[actors] def dropFromActorMap(path: String, childrenRef: Ref[Set[ActorRef[Any]]]): Task[Unit] =
    for {
      solvedPath          <- resolvePath(path)
      (_, _, _, actorName) = solvedPath
      _                   <- refActorMap.update(_ - actorName)
      children            <- childrenRef.get
      _                   <- ZIO.foreachDiscard(children)(_.stop)
      _                   <- childrenRef.set(Set.empty)
    } yield ()

  private def receiveLoop(address: ActorsConfig.Addr, port: ActorsConfig.Port): Task[Unit] =
    ZIO.scoped {
      for {
        addr      <- InetAddress.byName(address.value)
        address   <- InetSocketAddress.inetAddress(addr, port.value)
        p         <- Promise.make[Nothing, Unit]
        loopEffect = ZIO.scoped {
                       for {
                         _       <- ZIO.succeed(s"Binding $address")
                         channel <- AsynchronousServerSocketChannel.open
                         _       <- channel.bind(Some(address))

                         _ <- p.succeed(())

                         loop = for {
                                  worker          <- channel.accept
                                  obj             <- readFromWire(worker)
                                  envelope         = obj.asInstanceOf[Envelope]
                                  actorMap        <- refActorMap.get
                                  remoteActorPath <- resolvePath(envelope.recipient).map(_._4)
                                  _               <- actorMap.get(remoteActorPath) match {
                                                       case Some(actor) =>
                                                         for {
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
                                                           responseError <- ZIO.fail(new Exception("No such remote actor")).either
                                                           _             <- writeToWire(worker, responseError)
                                                         } yield ()
                                                     }
                                } yield ()
                         _   <- loop.forever
                       } yield ()
                     }
        _         <- loopEffect.fork
        _         <- p.await
      } yield ()
    }
}
