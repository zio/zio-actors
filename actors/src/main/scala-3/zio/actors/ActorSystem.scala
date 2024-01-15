package zio.actors

import zio.actors.ActorSystemUtils.*
import zio.actors.ActorsConfig.*
import zio.{ Ref, Task, UIO, ZIO }

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
      initActorRefMap <- Ref.make(Map.empty[String, Actor[?]])
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
  private val initActorRefMap: Ref[Map[String, Actor[?]]],
  private val parentActor: Option[String]
) extends BaseActorSystem(actorSystemName, config, remoteConfig, parentActor) {

  override private[actors] def refActorMap[F[+_]]: Ref[Map[String, Actor[F]]] =
    initActorRefMap.asInstanceOf[Ref[Map[String, Actor[F]]]]

  override private[actors] def newActorSystem[F[+_]](
    actorSystemName: String,
    config: Option[String],
    remoteConfig: Option[RemoteConfig],
    refActorMap: Ref[Map[String, Actor[F]]],
    parentActor: Option[String]
  ): ActorSystem =
    new ActorSystem(
      actorSystemName,
      config,
      remoteConfig,
      refActorMap.asInstanceOf[Ref[Map[String, Actor[?]]]],
      parentActor
    )

  override private[actors] def newChildrenRefSet[F[+_]]: UIO[Ref[Set[ActorRef[F]]]] =
    Ref.make(Set.empty[ActorRef[F]])

  override private[actors] def newContext[F[+_]](
    path: String,
    derivedSystem: ActorSystem,
    childrenSet: Ref[Set[ActorRef[F]]]
  ): Context = new Context(path, derivedSystem, childrenSet.asInstanceOf[Ref[Set[ActorRef[?]]]])

}
