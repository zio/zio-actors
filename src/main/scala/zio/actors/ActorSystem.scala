package zio.actors

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import java.nio.ByteBuffer

import zio.{Chunk, IO, Promise, Ref, UIO}
import zio.actors.Actor.Stateful
import zio.actors.ActorSystem.{Addr, Port}
import zio.nio.{Buffer, InetAddress, SocketAddress}
import zio.nio.channels.AsynchronousServerSocketChannel

object ActorSystem {

  type Addr = String
  type Port = Int

  def apply(name: String, remoteConfig: Option[(Addr, Port)]): IO[Any, ActorSystem] = for {

    initActorRefMap <- Ref.make(Map.empty[String, Any])

    actorSystem <- IO.effect(ActorSystemImpl(name, remoteConfig, initActorRefMap, parentActor = None))

    _ <- remoteConfig match {
      case Some((addr, port)) =>
        actorSystem.receiveLoop(addr, port)
      case None =>
        IO.unit
    }

  } yield actorSystem

  private[actors] def solvePath(path: String): IO[Exception, (String, Addr, Port, String)] = {

    val regex = "^(?:zio:\\/\\/)(\\w+)[@](\\d+\\.\\d+\\.\\d+\\.\\d+)[:](\\d+)[/]([\\w|\\/]+)$".r

    regex.findFirstMatchIn(path) match {
      case Some(value) if value.groupCount == 4 =>
        val actorSystemName = value.group(1)
        val address = value.group(2)
        val port = value.group(3).toInt
        val actorName = "/" + value.group(4)
        IO.succeed((actorSystemName, address, port, actorName))
      case None =>
        IO.fail(new Exception("Invalid qualified path"))
    }

  }

}

trait ActorSystem {

  def createActor[S, E >: Exception, F[+_]](name: String, sup: Supervisor[E], init: S, stateful: Stateful[S, E, F]): UIO[ActorRef[E, F]]

  def selectActor[E >: Exception, F[+_]](path: String): IO[E, ActorRef[E, F]]

}

case class ActorSystemImpl(name: String,
                           remoteConfig: Option[(Addr, Port)],
                           refActorMap: Ref[Map[String, Any]],
                           parentActor: Option[String]) extends ActorSystem {

  def receiveLoop(address: Addr, port: Port): IO[Any, Unit] = for {
    addr <- InetAddress.byName(address)
    address <- SocketAddress.inetSocketAddress(addr, port)
    p <- Promise.make[Nothing, Unit]
    _ <- (AsynchronousServerSocketChannel().use { channel =>
      for {
        _ <- channel.bind(address)

        _ <- p.succeed(())

        loop = channel.accept.use { worker =>

          for {
            size <- worker.read(4)
            buff <- Buffer.byte(size)
            d <- buff.asIntBuffer
            toRead <- d.get(0)

          //  _ <- zio.console.putStrLn("WORKED! " + toRead.toString)

            envelope <- worker.read(toRead)

            arr = envelope.toArray
            ois = new ObjectInputStream(new ByteArrayInputStream(arr)).readObject()

            obj = ois.asInstanceOf[Envelope]

            map <- refActorMap.get

            _ <- map.get("/" + obj.recipient.split("/").last) match {
              case Some(value) =>

                for {
                  resp <- value.asInstanceOf[Actor[Any, Any]].unsafeOp(obj.msg)
                  stream: ByteArrayOutputStream = new ByteArrayOutputStream()
                  oos <- UIO.effectTotal(new ObjectOutputStream(stream))
                  _ = oos.writeObject(resp)
                  _ = oos.close()
                  e = stream.toByteArray

                  _ <- worker.write(Chunk.fromArray(ByteBuffer.allocate(4).putInt(e.size).array()))
                  _ <- worker.write(Chunk.fromArray(e))

                } yield ()

              case None =>
                IO.unit
            }


          } yield ()

        }

        _ <- loop.forever

      } yield ()
    }).fork
    _ <- p.await

  } yield ()

  def createActor[S, E >: Exception, F[+_]](actname: String, sup: Supervisor[E], init: S, stateful: Stateful[S, E, F]): UIO[ActorRef[E, F]] = for {

    map <- refActorMap.get
    finalName = parentActor.getOrElse("") + "/" + actname
    actor <- Actor.stateful[S, E, F](sup, this.copy(parentActor = Some(finalName)))(init)(stateful)
    _ <- refActorMap.set(map + (finalName -> actor))

  } yield ActorRefLocal[E, F]("zio://" + name + "@" + remoteConfig.map({case (a, b) => a + ":" + b}).getOrElse("local") + finalName, actor)

  override def selectActor[E >: Exception, F[+_]](path: String): IO[E, ActorRef[E, F]] = for {

    solvedPath <- ActorSystem.solvePath(path)
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
            IO.fail(new Exception("No such actor"))
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
