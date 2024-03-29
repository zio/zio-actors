[//]: # (This file was autogenerated using `zio-sbt-website` plugin via `sbt generateReadme` command.)
[//]: # (So please do not edit it manually. Instead, change "docs/index.md" file or sbt setting keys)
[//]: # (e.g. "readmeDocumentation" and "readmeSupport".)

# ZIO Actors

[ZIO Actors](https://zio.dev/zio-actors) is a high-performance, purely functional library for building, composing, and supervising typed actors based on ZIO.

[![Production Ready](https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-actors/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio-actors_2.13.svg?label=Sonatype%20Release)](https://oss.sonatype.org/content/repositories/releases/dev/zio/zio-actors_2.13/) [![Sonatype Snapshots](https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio-actors_2.13.svg?label=Sonatype%20Snapshot)](https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio-actors_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-actors-docs_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-actors-docs_2.13) [![ZIO Actors](https://img.shields.io/github/stars/zio/zio-actors?style=social)](https://github.com/zio/zio-actors)

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

- **[Basics](docs/basics.md)**— Instantiating `ActorSystem`, defining actor's behavior, spawning actors.
- **[Supervision](docs/supervision.md)**— Short description of supervision functionality usage
- **[Remoting](docs/remoting.md)**— Defining remoting configuration, usage example, restrictions
- **[Persistence](docs/persistence.md)**— Event sourcing mechanism, datastore configuration
- **[Akka Interop](docs/akka-interop.md)**— Integration with akka typed actors.

## Installation

To use this library, we need to add the following line to our library dependencies in `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-actors" % "0.1.0"
```

Akka actors also has some other optional modules for persistence (which is useful for event sourcing) and integration with Akka toolkit:

```scala
libraryDependencies += "dev.zio" %% "zio-actors-persistence"      % "0.1.0"
libraryDependencies += "dev.zio" %% "zio-actors-persistence-jdbc" % "0.1.0"
libraryDependencies += "dev.zio" %% "zio-actors-akka-interop"     % "0.1.0"
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

## Documentation

Learn more on the [ZIO Actors homepage](https://zio.dev/zio-actors/)!

## Contributing

For the general guidelines, see ZIO [contributor's guide](https://zio.dev/about/contributing).

## Code of Conduct

See the [Code of Conduct](https://zio.dev/about/code-of-conduct)

## Support

Come chat with us on [![Badge-Discord]][Link-Discord].

[Badge-Discord]: https://img.shields.io/discord/629491597070827530?logo=discord "chat on discord"
[Link-Discord]: https://discord.gg/2ccFBr4 "Discord"

## License

[License](LICENSE)
