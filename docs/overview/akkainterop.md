---
id: overview_akkainterop
title: "Akka Interop"
---

Akka Interop gives you the ability to send and receive messages between zio actors and akka typed actors.

To use Akka Interops you need in your `build.sbt`:

```scala mdoc:passthrough

println(s"""```""")
if (zio.actors.BuildInfo.isSnapshot)
  println(s"""resolvers += Resolver.sonatypeRepo("snapshots")""")
println(s"""libraryDependencies += "dev.zio" %% "zio-actors-akka-interop" % "${zio.actors.BuildInfo.version}"""")
println(s"""```""")

```

Imports required for example:

```scala mdoc:silent
import zio.actors.Actor.Stateful
import zio.actors.{ ActorSystem, ActorRef, Context, Supervisor }
import zio.actors.akka.{ AkkaTypedActor, AkkaTypedActorRefLocal }
import zio.{ IO, Runtime }

import akka.actor.typed
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Scheduler
import akka.util.Timeout

import scala.concurrent.duration._
```

Case class for messages that zio actor send and receive from akka actor:

```scala mdoc:silent
sealed trait TypedMessage[+_]
case class PingToZio(zioReplyToActor: ActorRef[ZioMessage], msg: String) extends TypedMessage[Unit]
case class PingFromZio(zioSenderActor: ActorRef[ZioMessage]) extends TypedMessage[Unit]

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
      case PongFromAkka(msg) => IO.succeed((msg, ()))
      case Ping(akkaActor) =>
              for {
                 self <- context.self[ZioMessage]
                 _    <- akkaActor ! PingFromZio(self)
               } yield (state, ())
      case _=> IO.fail(new Exception("fail"))
    }
}
```

Akka actors ([Creation akka actors](https://doc.akka.io/docs/akka/current/typed/actor-lifecycle.html#creating-actors)), 
need a behavior, to define the messages to be handled, in this case send and receive messages to zio actor:
```scala mdoc:silent
object TestBehavior {
    lazy val zioRuntime = Runtime.default
    def apply(): Behavior[TypedMessage[_]] =
      Behaviors.receiveMessage { message =>
        message match {                  
          case PingToZio(zioReplyToActor, msgToZio) => zioRuntime.unsafeRun(zioReplyToActor ! PongFromAkka(msgToZio))
          case PingFromZio(zioSenderActor)          => zioRuntime.unsafeRun(zioSenderActor ! PongFromAkka("Pong from Akka"))
        }
        Behaviors.same
      }
  } 
```

We are ready to start sending messages from zio to akka, or vice versa via `fire-and-forget` interaction pattern,
but first we need to create a ZIO value with the created akka ActorRef(or ActorSystem), using `AkkaTypedActor.make`:
```scala mdoc:silent
for {
  akkaSystem <- IO(typed.ActorSystem(TestBehavior(), "akkaSystem"))
  system     <- ActorSystem("zioSystem")
  akkaActor  <- AkkaTypedActor.make(akkaSystem)
  zioActor   <- system.make("zioActor", Supervisor.none, "", handler)
  _          <- akkaActor ! PingToZio(zioActor, "Ping from Akka")
  _          <- zioActor ! Ping(akkaActor)
} yield ()
```

There's also `ask` interaction pattern, that provides a way to send a message to an akka actor and expect a response.
It's performed via `?` method, and needs a parameter of type `typed.ActorRef[R] => T` (`R` represents the response type, 
`T` is the message type), with implicit values for `akka.util.Timeout`  and `akka.actor.typed.Scheduler`:
```scala mdoc:silent
sealed trait AskMessage[+_]
case class PingAsk(value: Int, replyTo: typed.ActorRef[Int]) extends AskMessage[Int]

object AskTestBehavior {
    def apply(): Behavior[AskMessage[_]] =
      Behaviors.receiveMessage { message =>
        message match {
          case PingAsk(value, replyTo) => replyTo ! (value * 2)
        }
        Behaviors.same
      }
  }

def PingAskDeferred(value: Int): typed.ActorRef[Int] => PingAsk 
       = (hiddenRef: typed.ActorRef[Int]) => PingAsk(value, hiddenRef)

implicit val timeout: Timeout = 3.seconds
         
for {
  akkaAskSystem <- IO(typed.ActorSystem(AskTestBehavior(), "akkaSystem"))
  akkaActor <- AkkaTypedActor.make(akkaAskSystem)
  result    <- (akkaActor ? PingAskDeferred(1000)) (timeout, akkaAskSystem.scheduler)
} yield result == 2000
```
