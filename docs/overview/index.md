---
id: overview_index
title: "Contents"
---

ZIO Actors - a high-performance, purely-functional library for building, composing, and supervising typed actors backed by `ZIO`.
Here's list of contents available:

 - **[Basics](basics.md)** — Instantiating `ActorSystem`, defining actor's behavior, spawning actors.
 - **[Supervision](supervision.md)** — Short description of supervision functionality usage
 - **[Remoting](remoting.md)** — Defining remoting configuration, usage example, restrictions

## Installation

Include ZIO Actors in your project by adding the following to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-actors" % "0.1.0"
```
