---
id: about_index
title:  "About ZIO Actors"
---

High-performance, purely-functional library for building and supervising typed actors backed by ZIO

The [Actor Model](https://en.wikipedia.org/wiki/Actor_model) is used to build distributed highly scalable applications. The core concept behind the actor model is the ability to create multiple actors which run concurrently. The actor would receive a message do some computation on the message and then output a new message. Each actor runs independently of each other with no shared state between them and as such failure of one actor won't have an affect on the running of another. In its simplest form the goal of this project is to provide the ability to write actors in Functional Way that are typed leveraging [ZIO](https://github.com/zio/zio).



