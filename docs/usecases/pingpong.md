---
id: usecases_pingpong
title: "Ping Pong"
---

Here are type hierarchy and `Stateful` instance which can be used to create two actors performing basic ping-pong communication.

#### Configuration File at `./src/main/resources/application.conf`

```hocon
testSystemOne.zio.actors.remoting {
  hostname = "127.0.0.1"
  port = 8055
}
testSystemTwo.zio.actors.remoting {
  hostname = "127.0.0.1"
  port = 8056
}
```

#### Program

```scala mdoc:silent
import zio.actors.Actor.Stateful
import zio.actors._
import zio.IO
import zio.{ console, random }
import zio.console.Console.Live

sealed trait PingPong[+_]
case class Ping(sender: ActorRef[Throwable, PingPong])        extends PingPong[Unit]
case object Pong                                              extends PingPong[Unit]
case class GameInit(recipient: ActorRef[Throwable, PingPong]) extends PingPong[Unit]

val protoHandler = new Stateful[Unit, Throwable, PingPong] {
    override def receive[A](
      state: Unit,
      msg: PingPong[A],
      context: Context
    ): IO[Throwable, (Unit, A)] =
      msg match {
        case Ping(sender) =>
          for {
            _ <- console.putStrLn("Ping!").provide(Live)
            path <- sender.path
            _    <- sender ! Pong
          } yield ((), ())

        case Pong =>
          for {
            _ <- console.putStrLn("Pong!").provide(Live)
          } yield ((), ())

        case GameInit(to) =>
          for {
            self <- context.self[Throwable, PingPong]
            _    <- to ! Ping(self)
          } yield ((), ())
      }
  }

val program = for {
  actorSystemRoot <- ActorSystem("testSystemOne")
  one             <- actorSystemRoot.make("actorOne", Supervisor.none, (), protoHandler)

  actorSystem <- ActorSystem("testSystemTwo")
  _           <- actorSystem.make("actorTwo", Supervisor.none, (), protoHandler)

  remoteActor <- actorSystemRoot.select[Throwable, PingPong](
    "zio://testSystemTwo@127.0.0.1:8056/actorTwo"
  )

  _ <- one ! GameInit(remoteActor)

} yield ()
```
