---
id: index
title: "Introduction to ZIO Actors"
sidebar_label: "ZIO Actors"
---

ZIO Actors - a high-performance, purely-functional library for building, composing, and supervising typed actors backed by `ZIO`.

The [Actor Model](https://en.wikipedia.org/wiki/Actor_model) is used to build distributed highly scalable applications. The core concept behind the actor model is the ability to create multiple actors which run concurrently. The actor would receive a message do some computation on the message and then output a new message. Each actor runs independently of each other with no shared state between them and as such failure of one actor won't have an affect on the running of another. In its simplest form the goal of this project is to provide the ability to write actors in Functional Way that are typed leveraging [ZIO](https://github.com/zio/zio).

Here's list of contents available:

 - **[Basics](basics.md)** — Instantiating `ActorSystem`, defining actor's behavior, spawning actors.
 - **[Supervision](supervision.md)** — Short description of supervision functionality usage
 - **[Remoting](remoting.md)** — Defining remoting configuration, usage example, restrictions
 - **[Persistence](persistence.md)** - Event sourcing mechanism, datastore configuration
 - **[Akka Interop](akka-interop.md)** - Integration with akka typed actors.
 
## Installation

Include ZIO Actors in your project by adding the following to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-actors" % "@VERSION@"
```
