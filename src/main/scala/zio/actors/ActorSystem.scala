package zio.actors

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import java.nio.ByteBuffer

import zio.{Chunk, IO, Ref, UIO, ZIO}
import zio.actors.Actor.Stateful
import zio.actors.Main.Str
import zio.console.Console
import zio.nio.{Buffer, InetAddress, SocketAddress}
import zio.nio.channels.AsynchronousServerSocketChannel

object ActorSystem {

  def apply(name: String, config: Int): ZIO[Console, Any, ActorSystem] = for {

    ref <- Ref.make(Map.empty[String, Any])

    actorSystem <- IO.effect(ActorSystemImpl(name, config, ref, None))

    _ <- actorSystem.receiveLoop().fork

  } yield actorSystem

}

trait ActorSystem {

  def createActor[S, E >: Exception, F[+_]](name: String, sup: Supervisor[E], init: S, stateful: Stateful[S, E, F]): UIO[ActorRef[E, F]]

  def selectActor[E >: Exception, F[+_]](path: String): IO[E, ActorRef[E, F]]

}

case class ActorSystemImpl(name: String,
                           config: Int,
                           refActorMap: Ref[Map[String, Any]],
                           remoteSupportOpt: Option[AsynchronousServerSocketChannel]) extends ActorSystem {

  val path: String = name

  def receiveLoop() = for {
    addr <- InetAddress.localHost
    addres <- SocketAddress.inetSocketAddress(addr, config)
    _ <- AsynchronousServerSocketChannel().use { channel =>
      for {
        _ <- channel.bind(addres)

        rrr = channel.accept.use { worker =>

          for {
            size <- worker.read(4)
            buff <- Buffer.byte(size)
            d <- buff.asIntBuffer
            toRead <- d.get(0)

            _ <- zio.console.putStrLn("WORKED! " + toRead.toString)

            envelope <- worker.read(toRead)

            arr = envelope.toArray
            ois = new ObjectInputStream(new ByteArrayInputStream(arr)).readObject()

            obj = ois.asInstanceOf[Envelope]

            map <- refActorMap.get

            _ <- zio.console.putStrLn(obj.msg.asInstanceOf[Str].value)

            _ <- map.get(obj.recipient) match {
              case Some(value) =>

                for {
                  _ <- zio.console.putStrLn("Ahhh sweeet")
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
                zio.console.putStrLn("Not really")
            }


          } yield ()

        }

        _ <- rrr.forever

      } yield channel
    }

  } yield ()

  def createActor[S, E >: Exception, F[+_]](name: String, sup: Supervisor[E], init: S, stateful: Stateful[S, E, F]): UIO[ActorRef[E, F]] = for {

    actor <- Actor.stateful[S, E, F](sup)(init)(stateful)
    map <- refActorMap.get
    _ <- refActorMap.set(map + (name -> actor))

  } yield ActorRefLocal[E, F](actor)

  override def selectActor[E >: Exception, F[+_]](path: String): IO[E, ActorRef[E, F]] = for {

    d <- refActorMap.get

    actorRef <- d.get(path) match {
      case Some(value) if value.isInstanceOf[Actor[E, F]] =>
        for {
          t <- IO.effectTotal(value.asInstanceOf[Actor[E, F]])
        } yield ActorRefLocal(t)

      case None => //IO.fail(new Exception("No such actor"))
        for {

          e <- InetAddress.localHost
            .flatMap(iAddr => SocketAddress.inetSocketAddress(iAddr, 9099))

        } yield ActorRefRemote[E, F](e, path)
    }

  } yield actorRef

}