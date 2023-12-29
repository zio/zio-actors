---
id: akka-interop
title: "Akka Interop"
---

Akka Interop gives you the ability to send and receive messages between zio actors and akka typed actors.

To use Akka Interops you need in your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-actors-akka-interop" % "@VERSION@"
```

Imports required for example:

```scala mdoc:silent
import zio.actors.Actor.Stateful
import zio.actors.{ ActorSystem, ActorRef, Context, Supervisor }
import zio.actors.akka.{ AkkaTypedActor, AkkaTypedActorRefLocal }
import zio.{ ZIO, IO, Runtime }

import akka.actor.typed
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Scheduler
import akka.util.Timeout

import scala.concurrent.duration._
```

Case class for messages that zio actor send and receive from akka actor:

```scala mdoc:silent
sealed trait TypedMessage
case class PingToZio(zioReplyToActor: ActorRef[ZioMessage], msg: String) extends TypedMessage
case class PingFromZio(zioSenderActor: ActorRef[ZioMessage]) extends TypedMessage

sealed trait ZioMessage[+_]
case class PongFromAkka(msg: String) extends ZioMessage[Unit]
case class Ping(akkaActor: AkkaTypedActorRefLocal[TypedMessage]) extends ZioMessage[Unit]
```

For zio actor basics, ([Basics section](basics.md#usage)).
Here's the `Stateful` implementation for our zio actor:

```scala mdoc:silent
val handler = new Stateful[Any, String, ZioMessage] {
  override def receive[A](state: String, msg: ZioMessage[A], context: Context): IO[Throwable, (String, A)] =
    msg match {
      case PongFromAkka(msg) => ZIO.succeed((msg, ()))
      case Ping(akkaActor) =>
              for {
                 self <- context.self[ZioMessage]
                 _    <- akkaActor ! PingFromZio(self)
               } yield (state, ())
      case _=> ZIO.fail(new Exception("fail"))
    }
}
```

Akka actors ([Creation akka actors](https://doc.akka.io/docs/akka/current/typed/actor-lifecycle.html#creating-actors)),
need a behavior, to define the messages to be handled, in this case send and receive messages to zio actor:

```scala mdoc:silent
object TestBehavior {
    lazy val zioRuntime = Runtime.default
    def apply(): Behavior[TypedMessage] =
      Behaviors.receiveMessage { message =>
        message match {
          case PingToZio(zioReplyToActor, msgToZio) =>
            zio.Unsafe.unsafe { implicit unsafe =>
              zioRuntime.unsafe.run(zioReplyToActor ! PongFromAkka(msgToZio))
            }
          case PingFromZio(zioSenderActor)          =>
            zio.Unsafe.unsafe { implicit unsafe =>
              zioRuntime.unsafe.run(zioSenderActor ! PongFromAkka("Pong from Akka"))
            }
        }
        Behaviors.same
      }
  }
```

We are ready to start sending messages from zio to akka, or vice versa via `fire-and-forget` interaction pattern,
but first we need to create a ZIO value with the created akka ActorRef(or ActorSystem), using `AkkaTypedActor.make`:

```scala mdoc:silent
for {
  akkaSystem <- ZIO.succeed(typed.ActorSystem(TestBehavior(), "akkaSystem"))
  system     <- ActorSystem("zioSystem")
  akkaActor  <- AkkaTypedActor.make(akkaSystem)
  zioActor   <- system.make("zioActor", Supervisor.none, "", handler)
  _          <- akkaActor ! PingToZio(zioActor, "Ping from Akka")
  _          <- zioActor ! Ping(akkaActor)
} yield ()
```

There's also `ask` interaction pattern, that provides a way to send a message to an akka actor and expect a response.
It's performed via `?` method, and needs a parameter of type `typed.ActorRef[R] => T` (`R` represents the response type,
`T` is the message type), with implicit values for `akka.util.Timeout` and `akka.actor.typed.Scheduler`:

```scala mdoc:silent
sealed trait AskMessage
case class PingAsk(value: Int, replyTo: typed.ActorRef[Int]) extends AskMessage

object AskTestBehavior {
    def apply(): Behavior[AskMessage] =
      Behaviors.receiveMessage { message =>
        message match {
          case PingAsk(value, replyTo) => replyTo ! (value * 2)
        }
        Behaviors.same
      }
  }

def PingAskDeferred(value: Int)(hiddenRef: typed.ActorRef[Int]): PingAsk =
  PingAsk(value, hiddenRef)

import scala.concurrent.duration.DurationInt
implicit val timeout: Timeout = 3.seconds

for {
  akkaAskSystem <- ZIO.succeed(typed.ActorSystem(AskTestBehavior(), "akkaSystem"))
  akkaActor <- AkkaTypedActor.make(akkaAskSystem)
  result    <- (akkaActor ? PingAskDeferred(1000)) (timeout, akkaAskSystem.scheduler)
} yield result == 2000
```
