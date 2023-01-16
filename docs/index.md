---
id: index
title: "Introduction to ZIO Actors"
sidebar_label: "ZIO Actors"
---

[ZIO Actors](https://zio.dev/zio-actors) is a high-performance, purely functional library for building, composing, and supervising typed actors based on ZIO.

@PROJECT_BADGES@

## Introduction

The [Actor Model](https://en.wikipedia.org/wiki/Actor_model) is used to build distributed highly scalable applications. The core concept behind the actor model is the ability to create multiple actors which run concurrently. The actor would receive a message do some computation on the message and then output a new message. Each actor runs independently of each other with no shared state between them and as such failure of one actor won't have an affect on the running of another. In its simplest form the goal of this project is to provide the ability to write actors in Functional Way that are typed leveraging [ZIO](https://github.com/zio/zio).

ZIO Actors is based on the _Actor Model_ which is a conceptual model of concurrent computation. In the actor model, the _actor_ is the fundamental unit of computation, unlike the ZIO concurrency model, which is the fiber.

Each actor has a mailbox that stores and processes the incoming messages in FIFO order. An actor allowed to:
- create another actor.
- send a message to itself or other actors.
- handle the incoming message, and:
    - decide **what to do** based on the current state and the received message.
    - decide **what is the next state** based on the current state and the received message.

Some characteristics of an _Actor Model_:

- **Isolated State** — Each actor holds its private state. They only have access to their internal state. They are isolated from each other, and they do not share the memory. The only way to change the state of an actor is to send a message to that actor.

- **Process of One Message at a Time** — Each actor handles and processes one message at a time. They read messages from their inboxes and process them sequentially.

- **Actor Persistence** — A persistent actor records its state as events. The actor can recover its state from persisted events after a crash or restart.

- **Remote Messaging** — Actors can communicate with each other only through messages. They can run locally or remotely on another machine. Remote actors can communicate with each other transparently as if there are located locally.

- **Actor Supervision** — Parent actors can supervise their child actors. For example, if a child actor fails, the supervisor actor can restart that actor.

Here's list of contents available:

- **[Basics](basics.md)**— Instantiating `ActorSystem`, defining actor's behavior, spawning actors.
- **[Supervision](supervision.md)**— Short description of supervision functionality usage
- **[Remoting](remoting.md)**— Defining remoting configuration, usage example, restrictions
- **[Persistence](persistence.md)**— Event sourcing mechanism, datastore configuration
- **[Akka Interop](akka-interop.md)**— Integration with akka typed actors.

## Installation

To use this library, we need to add the following line to our library dependencies in `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-actors" % "@VERSION@"
```

Akka actors also has some other optional modules for persistence (which is useful for event sourcing) and integration with Akka toolkit:

```scala
libraryDependencies += "dev.zio" %% "zio-actors-persistence"      % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-actors-persistence-jdbc" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-actors-akka-interop"     % "@VERSION@"
```

## Example

Let's try to implement a simple Counter Actor which receives two `Increase` and `Get` commands:

[//]: # (TODO: make snippet type-checked using mdoc)

```scala
import zio.actors.Actor.Stateful
import zio.actors._
import zio.clock.Clock
import zio.console.putStrLn
import zio.{ExitCode, UIO, URIO, ZIO}

sealed trait Message[+_]
case object Increase extends Message[Unit]
case object Get      extends Message[Int]

object CounterActorExample extends zio.App {

  // Definition of stateful actor
  val counterActor: Stateful[Any, Int, Message] =
    new Stateful[Any, Int, Message] {
      override def receive[A](
          state: Int,
          msg: Message[A],
          context: Context
      ): UIO[(Int, A)] =
        msg match {
          case Increase => UIO((state + 1, ()))
          case Get      => UIO((state, state))
        }
    }

  val myApp: ZIO[Clock, Throwable, Int] =
    for {
      system <- ActorSystem("MyActorSystem")
      actor  <- system.make("counter", Supervisor.none, 0, counterActor)
      _      <- actor ! Increase
      _      <- actor ! Increase
      s      <- actor ? Get
    } yield s

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    myApp
      .flatMap(state => putStrLn(s"The final state of counter: $state"))
      .exitCode
}
```

## Resources

- [Acting Lessons for Scala Engineers with Akka and ZIO](https://www.youtube.com/watch?v=AQXBlbkf9wc) by [Salar Rahmanian](https://wwww.softinio.com) (November 2020)
- [Introduction to ZIO Actors](https://www.softinio.com/post/introduction-to-zio-actors/) by [Salar Rahmanian](https://www.softinio.com) (November 2020)

