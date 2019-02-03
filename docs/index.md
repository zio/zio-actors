---
layout: home
position: 1
section: home
title: "Home"
---

# Welcome to Scalaz Actors

A high-performance, purely-functional library for building, composing, and supervising typed actors based on Scalaz ZIO

The [Actor Model](https://en.wikipedia.org/wiki/Actor_model) is used to build distributed highly scaleable applications. The core concept behind the actor model is the ability to create multiple actors which run concurrently. The actor would receive a message do some computation on the message and then output a new message. Each actor runs independently of each other with no shared state between them and as such failure of one actor won't have an affect on the running of another.

In its simplest form the goal of this project is to provide the ability to write actors in Functional Way that are typed leveraging [scalaz-zio](https://github.com/scalaz/scalaz-zio).

To learn more about how Scalaz Actors can help you accomplish the impossible, see [Getting Started](getting_started.html) and [Overview](overview.html).

# Scalaz Actors current alternatives
- [Akka](https://akka.io) (Scala & Java)

- [Akka .net](https://getakka.net) (C#)

- [Orleans](https://dotnet.github.io/orleans/) (C#)

- [Erlang/Otp](http://www.erlang.org) (Erlang)

- [Elixir](https://elixir-lang.org) (Elixir)

We differentiate ourselves from the above competition by having the following benefits: 
- Purely Functional
- Everything Typed
- Light Weight

# Background and Example

- [Scalaz 8 vs Akka Actors](https://www.youtube.com/watch?v=Eihz7kqn6mU)
- [Scalaz 8 vs Akka Actors slides](https://www.slideshare.net/jdegoes/scalaz-8-vs-akka-actors)


