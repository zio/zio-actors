package zio.actors

import zio.actors.LocalActorSystem._
import zio.actors.Actor.AbstractStateful
import zio.actors.config.{ Addr, Port, RemoteConfig }
import zio.clock.Clock
import zio._

class LocalActorSystem(
  val actorSystemName: String,
  val config: Option[String],
  private val remoteConfig: Option[RemoteConfig],
  private val refActorMap: Ref[Map[String, Any]],
  private val parentActor: Option[String]
) {

  /**
   * Creates actor and registers it to dependent actor system
   *
   * @param actorName name of the actor
   * @param sup       - supervision strategy
   * @param init      - initial state
   * @param stateful  - actor's behavior description
   * @tparam S - state type
   * @tparam F - DSL type
   * @return reference to the created actor in effect that can't fail
   */
  def make[R, S, F[+_]](
    actorName: String,
    sup: zio.actors.Supervisor[R],
    init: S,
    stateful: AbstractStateful[R, S, F]
  ): RIO[R with Clock, ActorRef[F]] =
    for {
      map          <- refActorMap.get
      finalName    <- buildFinalName(parentActor.getOrElse(""), actorName)
      _            <- if (map.contains(finalName)) IO.fail(new Exception(s"Actor $finalName already exists")) else IO.unit
      path          = buildPath(actorSystemName, finalName, remoteConfig)
      derivedSystem = newActorSystem(finalName)
      childrenSet  <- Ref.make(Set.empty[ActorRef[Any]])
      actor        <- stateful.makeActor(
                        sup,
                        new Context(path, derivedSystem, childrenSet),
                        () => dropFromActorMap(path, childrenSet)
                      )(init)
      _            <- refActorMap.set(map + (finalName -> actor))
    } yield new ActorRefLocal[F](path, actor)

  protected def newActorSystem[F[+_], S, R](finalName: String) =
    new LocalActorSystem(actorSystemName, config, remoteConfig, refActorMap, Some(finalName))

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
  def select[F[+_]](path: String): Task[ActorRef[F]] =
    for {
      solvedPath                 <- resolvePath(path)
      (_, _, _, actorName)        = solvedPath
      actorMap                   <- refActorMap.get
      actorRef: ActorRefLocal[F] <- createActorLocalRef(path, actorName, actorMap)
    } yield actorRef

  protected def createActorLocalRef[F[+_]](
    path: String,
    actorName: String,
    actorMap: Map[String, Any]
  ): ZIO[Any, Exception, ActorRefLocal[F]] =
    for {
      actorRef <- actorMap.get(actorName) match {
                    case Some(value) =>
                      for {
                        actor <- IO.effectTotal(value.asInstanceOf[Actor[F]])
                      } yield new ActorRefLocal(path, actor)
                    case None        =>
                      IO.fail(new Exception(s"No such actor $actorName in local ActorSystem."))
                  }
    } yield actorRef

  /**
   * Stops all actors within this ActorSystem.
   *
   * @return all actors' unprocessed messages
   */
  def shutdown: Task[List[_]] =
    for {
      systemActors <- refActorMap.get
      actorsDump   <- ZIO.foreach(systemActors.values.toList)(_.asInstanceOf[Actor[Any]].stop)
    } yield actorsDump.flatten

  /* INTERNAL API */

  private[actors] def dropFromActorMap(path: String, childrenRef: Ref[Set[ActorRef[Any]]]) =
    for {
      solvedPath          <- resolvePath(path)
      (_, _, _, actorName) = solvedPath
      _                   <- refActorMap.update(_ - actorName)
      children            <- childrenRef.get
      _                   <- ZIO.foreach_(children)(_.stop)
      _                   <- childrenRef.set(Set.empty)
    } yield ()
}

object LocalActorSystem {
  private val RegexName = "[\\w+|\\d+|(\\-_.*$+:@&=,!~';.)|\\/]+".r

  private val RegexFullPath =
    "^(?:zio:\\/\\/)(\\w+)[@](\\d+\\.\\d+\\.\\d+\\.\\d+)[:](\\d+)[/]([\\w+|\\d+|\\-_.*$+:@&=,!~';.|\\/]+)$".r

  def resolvePath(path: String): Task[(String, Addr, Port, String)] =
    RegexFullPath.findFirstMatchIn(path) match {
      case Some(value) if value.groupCount == 4 =>
        val actorSystemName = value.group(1)
        val address         = Addr(value.group(2))
        val port            = Port(value.group(3).toInt)
        val actorName       = "/" + value.group(4)
        IO.succeed((actorSystemName, address, port, actorName))
      case None                                 =>
        IO.fail(
          new Exception(
            "Invalid path provided. The pattern is zio://YOUR_ACTOR_SYSTEM_NAME@ADDRES:PORT/RELATIVE_ACTOR_PATH"
          )
        )
    }

  private[actors] def buildFinalName(parentActorName: String, actorName: String) =
    actorName match {
      case ""            => IO.fail(new Exception("Actor actor must not be empty"))
      case null          => IO.fail(new Exception("Actor actor must not be null"))
      case RegexName(_*) => UIO.effectTotal(parentActorName + "/" + actorName)
      case _             => IO.fail(new Exception(s"Invalid actor name provided $actorName. Valid symbols are -_.*$$+:@&=,!~';"))
    }

  def buildPath(actorSystemName: String, actorPath: String, remoteConfig: Option[RemoteConfig]) =
    s"zio://$actorSystemName@${remoteConfig.map(c => c.addr.value + ":" + c.port.value).getOrElse("0.0.0.0:0000")}$actorPath"
}
