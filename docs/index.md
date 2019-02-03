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
