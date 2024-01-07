package zio.actors

import zio.{Chunk, Task, ZIO}
import zio.actors.ActorsConfig.{Addr, Port, RemoteConfig}
import zio.nio.Buffer
import zio.nio.channels.AsynchronousSocketChannel

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File, ObjectInputStream, ObjectOutputStream}
import java.nio.ByteBuffer
import scala.io.Source

/* INTERNAL API */

private[actors] object ActorSystemUtils {

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
        ZIO.succeed((actorSystemName, address, port, actorName))
      case _                                    =>
        ZIO.fail(
          new Exception(
            "Invalid path provided. The pattern is zio://YOUR_ACTOR_SYSTEM_NAME@ADDRES:PORT/RELATIVE_ACTOR_PATH"
          )
        )
    }

  private[actors] def buildFinalName(parentActorName: String, actorName: String): Task[String] =
    actorName match {
      case ""            => ZIO.fail(new Exception("Actor actor must not be empty"))
      case null          => ZIO.fail(new Exception("Actor actor must not be null"))
      case RegexName(_*) => ZIO.succeed(parentActorName + "/" + actorName)
      case _             => ZIO.fail(new Exception(s"Invalid actor name provided $actorName. Valid symbols are -_.*$$+:@&=,!~';"))
    }

  def buildPath(actorSystemName: String, actorPath: String, remoteConfig: Option[RemoteConfig]): String =
    s"zio://$actorSystemName@${remoteConfig.map(c => c.addr.value + ":" + c.port.value).getOrElse("0.0.0.0:0000")}$actorPath"

  def retrieveConfig(configFile: Option[File]): Task[Option[String]] =
    configFile.fold[Task[Option[String]]](ZIO.none) { file =>
      ZIO.scoped {
        ZIO
          .acquireRelease(ZIO.attempt(Source.fromFile(file)))(f => ZIO.succeed(f.close()))
          .flatMap(s => ZIO.some(s.mkString))
      }
    }

  def retrieveRemoteConfig(sysName: String, configStr: Option[String]): Task[Option[RemoteConfig]] =
    configStr.fold[Task[Option[RemoteConfig]]](ZIO.none)(file => ActorsConfig.getRemoteConfig(sysName, file))

  def objFromByteArray(bytes: Array[Byte]): Task[Any] =
    ZIO.scoped {
      ZIO
        .acquireRelease(ZIO.attempt(new ObjectInputStream(new ByteArrayInputStream(bytes))))(s =>
          ZIO.succeed(s.close())
        )
        .flatMap { s =>
          ZIO.attempt(s.readObject())
        }
    }

  def readFromWire(socket: AsynchronousSocketChannel): Task[Any] =
    for {
      size      <- socket.readChunk(4)
      buffer    <- Buffer.byte(size)
      intBuffer <- buffer.asIntBuffer
      toRead    <- intBuffer.get(0)
      content   <- socket.readChunk(toRead)
      bytes      = content.toArray
      obj       <- objFromByteArray(bytes)
    } yield obj

  def objToByteArray(obj: Any): Task[Array[Byte]] =
    for {
      stream <- ZIO.succeed(new ByteArrayOutputStream())
      bytes  <- ZIO.scoped {
        ZIO.acquireRelease(ZIO.attempt(new ObjectOutputStream(stream)))(s => ZIO.succeed(s.close())).flatMap {
          s =>
            ZIO.attempt(s.writeObject(obj)) *> ZIO.succeed(stream.toByteArray)
        }
      }
    } yield bytes

  def writeToWire(socket: AsynchronousSocketChannel, obj: Any): Task[Unit] =
    for {
      bytes <- objToByteArray(obj)
      _     <- socket.writeChunk(Chunk.fromArray(ByteBuffer.allocate(4).putInt(bytes.length).array()))
      _     <- socket.writeChunk(Chunk.fromArray(bytes))
    } yield ()
}