package zio.actors

import zio.actors.Actor.AbstractStateful
import zio.actors.ActorSystemUtils.*
import zio.actors.ActorsConfig.*
import zio.{ RIO, Ref, Task, ZIO }

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
  override private[actors] val actorSystemName: String,
  override private[actors] val config: Option[String],
  private val remoteConfig: Option[RemoteConfig],
  private val initActorRefMap: Ref[Map[String, Actor[Any]]],
  private val parentActor: Option[String]
) extends BaseActorSystem(actorSystemName, config) {

  override private[actors] def refActorMap[F[+_]]: Ref[Map[String, Actor[F]]] =
    initActorRefMap.asInstanceOf[Ref[Map[String, Actor[F]]]]

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
      derivedSystem = new ActorSystem(
                        actorSystemName,
                        config,
                        remoteConfig,
                        refActorMap.asInstanceOf[Ref[Map[String, Actor[Any]]]],
                        Some(finalName)
                      )
      childrenSet  <- Ref.make(Set.empty[ActorRef[Any]])
      actor        <- stateful.makeActor(
                        sup,
                        new Context(path, derivedSystem, childrenSet),
                        () => dropFromActorMap[Any](path, childrenSet)
                      )(init)
      _            <- refActorMap.set(map + (finalName -> actor.asInstanceOf[Actor[Any]]))
    } yield new ActorRefLocal[F](path, actor)
}
