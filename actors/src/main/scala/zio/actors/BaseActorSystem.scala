package zio.actors

import zio.actors.Actor.AbstractStateful
import zio.actors.ActorSystemUtils._
import zio.actors.ActorsConfig.RemoteConfig
import zio.nio.channels.AsynchronousServerSocketChannel
import zio.nio.{ InetAddress, InetSocketAddress }
import zio.{ Promise, RIO, Ref, Task, UIO, ZIO }

/**
 * Base class for the ActorSystem which represents a running instance of the actor system provisioning actor herding,
 * remoting, actor creation and selection.
 */
private[actors] abstract class BaseActorSystem private[actors] (
  private[actors] val actorSystemName: String,
  private[actors] val config: Option[String],
  private val remoteConfig: Option[RemoteConfig],
  private val parentActor: Option[String]
) {

  /**
   * Getter for the mutable reference containing all actors within this ActorSystem
   *
   * @tparam F
   *   \- actor's DSL type
   * @return
   *   mutable reference containing all actors within this ActorSystem
   */
  private[actors] def refActorMap[F[+_]]: Ref[Map[String, Actor[F]]]

  /**
   * Returns a new actor system
   */
  private[actors] def newActorSystem[F[+_]](
    actorSystemName: String,
    config: Option[String],
    remoteConfig: Option[RemoteConfig],
    refActorMap: Ref[Map[String, Actor[F]]],
    parentActor: Option[String]
  ): ActorSystem

  /**
   * Returns an effect which contains a new mutable reference containing all child references
   */
  private[actors] def newChildrenRefSet[F[+_]]: UIO[Ref[Set[ActorRef[F]]]]

  /**
   * Returns a new Context
   */
  private[actors] def newContext[F[+_]](
    path: String,
    derivedSystem: ActorSystem,
    childrenSet: Ref[Set[ActorRef[F]]]
  ): Context

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
      derivedSystem = newActorSystem(actorSystemName, config, remoteConfig, refActorMap, Some(finalName))
      childrenSet  <- newChildrenRefSet[F]
      actor        <- stateful.makeActor(
                        sup,
                        newContext(path, derivedSystem, childrenSet),
                        () => dropFromActorMap[F](path, childrenSet)
                      )(init)
      _            <- refActorMap.set(map + (finalName -> actor))
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
      actorMap                               <- refActorMap.get
      actorRef                               <-
        if (pathActSysName == actorSystemName)
          ZIO
            .succeed(actorMap.get(actorName))
            .someOrFail(new Exception(s"No such actor $actorName in local ActorSystem."))
            .map(actor => new ActorRefLocal(path, actor))
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
  def shutdown: Task[List[_]] =
    for {
      systemActors <- refActorMap.get
      actorsDump   <- ZIO.foreach(systemActors.values.toList)(_.stop)
    } yield actorsDump.flatten

  /* INTERNAL API */

  private[actors] def dropFromActorMap[F[+_]](path: String, childrenRef: Ref[Set[ActorRef[F]]]): Task[Unit] =
    for {
      solvedPath          <- resolvePath(path)
      (_, _, _, actorName) = solvedPath
      _                   <- refActorMap.update((a: Map[String, Actor[F]]) => a - actorName)
      children            <- childrenRef.get
      _                   <- ZIO.foreachDiscard(children)(_.stop)
      _                   <- childrenRef.set(Set.empty)
    } yield ()

  private[actors] def receiveLoop(address: ActorsConfig.Addr, port: ActorsConfig.Port): Task[Unit] =
    ZIO.scoped {
      for {
        addr      <- InetAddress.byName(address.value)
        address   <- InetSocketAddress.inetAddress(addr, port.value)
        p         <- Promise.make[Nothing, Unit]
        loopEffect = ZIO.scoped {
                       for {
                         _       <- ZIO.debug(s"Binding $address")
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
