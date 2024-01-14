package zio.actors

import zio.actors.ActorSystemUtils.*
import zio.nio.channels.AsynchronousServerSocketChannel
import zio.nio.{ InetAddress, InetSocketAddress }
import zio.{ Promise, Ref, Task, ZIO }

/**
 * Type representing running instance of actor system provisioning actor herding, remoting and actor creation and
 * selection.
 */
abstract class BaseActorSystem private[actors] (
  private[actors] val actorSystemName: String,
  private[actors] val config: Option[String]
) {

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
  private[actors] def refActorMap[F[+_]]: Ref[Map[String, Actor[F]]]

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
  def shutdown: Task[List[?]] =
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
