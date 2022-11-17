---
id: examples
title: "Examples"
---

So now how to use it? Here you can find some examples to dive into:

- **[Ping Pong](#ping-pong-example)** — Example of `fire-and-forget` ping-pong with remote actor lookup
- Also there are project samples in `examples` root directory of the repo.
They are meant to be a counterpart of [akka-samples](https://github.com/akka/akka-samples) for `zio-actors`.

## Ping Pong Example

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
import zio.RIO
import zio._

sealed trait PingPong[+_]
case class Ping(sender: ActorRef[PingPong])        extends PingPong[Unit]
case object Pong                                   extends PingPong[Unit]
case class GameInit(recipient: ActorRef[PingPong]) extends PingPong[Unit]

val protoHandler = new Stateful[Console, Unit, PingPong] {
    override def receive[A](
      state: Unit,
      msg: PingPong[A],
      context: Context
    ): RIO[Console, (Unit, A)] =
      msg match {
        case Ping(sender) =>
          for {
            _    <- Console.printLine("Ping!")
            path <- sender.path
            _    <- sender ! Pong
          } yield ((), ())

        case Pong =>
          for {
            _ <- Console.printLine("Pong!")
          } yield ((), ())

        case GameInit(to) =>
          for {
            self <- context.self[PingPong]
            _    <- to ! Ping(self)
          } yield ((), ())
      }
  }

val program = for {
  actorSystemRoot <- ActorSystem("testSystemOne")
  one             <- actorSystemRoot.make("actorOne", zio.actors.Supervisor.none, (), protoHandler)

  actorSystem <- ActorSystem("testSystemTwo")
  _           <- actorSystem.make("actorTwo", zio.actors.Supervisor.none, (), protoHandler)

  remoteActor <- actorSystemRoot.select[PingPong](
    "zio://testSystemTwo@127.0.0.1:8056/actorTwo"
  )

  _ <- one ! GameInit(remoteActor)

} yield ()
```
