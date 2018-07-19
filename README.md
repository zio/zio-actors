# scalaz-actors

[![Gitter](https://badges.gitter.im/scalaz/scalaz-actors.svg)](https://gitter.im/scalaz/scalaz-actors?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

# Summary / Goal
High-performance, purely-functional library for building, composing, and supervising typed actors based on Scalaz ZIO.

# Introduction / Highlights
The [Actor Model](https://en.wikipedia.org/wiki/Actor_model) is used to build distributed highly scaleable applications. The core concept behind the actor model is the ability to create multiple actors which run concurrently. The actor would receive a message do some computation on the message and then output a new message. Each actor runs independently of each other with no shared state between them and as such failure of one actor won't have an affect on the running of another.

In its simplest form the goal of this project is to provide the ability to write actors in Functional Way that are typed leveraging [scalaz-zio](https://github.com/scalaz/scalaz-zio). However for a fully usable system will need the following components:

- Supervision

- Actor Mailbox/Queue

- Actor Monitoring and Metrics

- Actor persistence

- Message Stashing

- Message Routing / Actor Registry

This summarizes the goals of this project well. We will be adding a detailed
technical design document explaining everything in more details here on the
project wiki: [Technical Design Document](https://github.com/scalaz/scalaz-actors/wiki/Design)


# Competition
- [Akka](https://akka.io) (Scala & Java)

- [Akka .net](https://getakka.net) (C#)

- [Orleans](https://dotnet.github.io/orleans/) (C#)

- [Erlang/Otp](http://www.erlang.org) (Erlang)

- [Elixir](https://elixir-lang.org) (Elixir)

# Background and Example
[Scalaz 8 vs Akka Actors](https://www.youtube.com/watch?v=Eihz7kqn6mU)

# Project Documentation
Project related documentation will be here:

[Project Design and Documentation](https://github.com/scalaz/scalaz-actors/wiki)

# Library Documentation
Coming soon !

# Current Project Contributors
[Current Team](https://github.com/scalaz/scalaz-actors/wiki/Team)

## Contributing
[Documentation for contributors](CONTRIBUTING.md)

