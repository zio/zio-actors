---
id: overview_index
title: "Contents"
---

ZIO Actors - a high-performance, purely-functional library for building, composing, and supervising typed actors backed by `ZIO`.
Here's list of contents available:

 - **[Basics](basics.md)** — Instantiating `ActorSystem`, defining actor's behavior, spawning actors.
 - **[Supervision](supervision.md)** — Short description of supervision functionality usage
 - **[Remoting](remoting.md)** — Defining remoting configuration, usage example, restrictions
 - **[Persistence](persistence.md)** - Event sourcing mechanism, datastore configuration
 - **[Akka Interop](akkainterop.md)** - Integration with akka typed actors.
 
## Installation

Include ZIO Actors in your project by adding the following to your `build.sbt`:

```scala mdoc:passthrough

println(s"""```""")
if (zio.actors.BuildInfo.isSnapshot)
  println(s"""resolvers += Resolver.sonatypeRepo("snapshots")""")
println(s"""libraryDependencies += "dev.zio" %% "zio-actors" % "${zio.actors.BuildInfo.version}"""")
println(s"""```""")

```
