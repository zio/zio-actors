package zio.actors

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import java.nio.ByteBuffer

import zio.nio.{Buffer, InetSocketAddress}
import zio.nio.channels.AsynchronousSocketChannel
import zio.{Chunk, IO, UIO}

trait ActorRef[+E >: Exception, -F[+_]] {

  def ![A](fa: F[A]): IO[E, A]

  def spawn[E2 >: Exception, F2[+_]](): UIO[ActorRef[E2, F2]]

}

case class ActorRefLocal[+E >: Exception, -F[+_]](private val actor: Actor[E, F]) extends ActorRef[E, F] {

  override def ![A](fa: F[A]): IO[E, A] = for {
    res <- actor.!(fa)
  } yield res

  override def spawn[E2  >: Exception, F2[+_]](): UIO[ActorRef[E2, F2]] = ???

}

case class ActorRefRemote[+E >: Exception, -F[+_]](private val address: InetSocketAddress,
                                                private val path: String) extends ActorRef[E, F] {


  override def ![A](fa: F[A]): IO[E, A] = (for {

    stream <- IO.effectTotal(new ByteArrayOutputStream())
    oos <- UIO.effectTotal(new ObjectOutputStream(stream))
    _ = oos.writeObject(Envelope(fa, path))
    _ = oos.close()
    e = stream.toByteArray

    size = e.size

    www <- AsynchronousSocketChannel().use { client =>

      for {
        _ <- client.connect(address)

        _ <- client.write(Chunk.fromArray(ByteBuffer.allocate(4).putInt(size).array()))

        _ <- client.write(Chunk.fromArray(e))

        _ <- zio.console.putStrLn("UUUUUUU")

        size <- client.read(4)

        _ <- zio.console.putStrLn("UUUUU2UU")

        t <- Buffer.byte(size)

        y <- t.asIntBuffer

        toRead <- y.get(0)

        result <- client.read(toRead)

        arr = result.toArray

        ois = new ObjectInputStream(new ByteArrayInputStream(arr)).readObject()

      } yield ois.asInstanceOf[A]

    }

  } yield www).asInstanceOf[IO[E, A]]

  override def spawn[E2 >: Exception, F2[+_]](): UIO[ActorRef[E2, F2]] = ???

}
