# scalaz-actors

[![Gitter](https://badges.gitter.im/scalaz/scalaz-actors.svg)](https://gitter.im/scalaz/scalaz-actors?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

# Summary / Goal
High-performance, purely-functional library for building, composing, and supervising typed actors based on Scalaz ZIO.

# Introduction / Highlights
The [Actor Model](https://en.wikipedia.org/wiki/Actor_model) is used to build distributed highly scaleable applications. The core concept behind the actor model is the ability to create multiple actors which run concurrently. The actor would receive a message do some computation on the message and then output a new message. Each actor runs independently of each other with no shared state between them and as such failure of one actor won't have an affect on the running of another.

In its simplest form the goal of this project is to provide the ability to write actors in Functional Way that are typed leveraging [scalaz-zio](https://github.com/scalaz/scalaz-zio). However for a fully usable system will need the following components:

## Supervision
A supervisors responsability is to manage actors. It is responsible for: 

- Taking the next message from the actor mailbox and running processing this message by starting a new os process using the business logic defined by the actor
- Keeping track of the all running processes
- Handling Failure of process
- Applying retry logic to failed process
- State management of each process running. This is done in-memory

## Actor Mailbox/Queue
- Each Actor will have a FIFO mailbox by default used to queues messages waiting to be processed
- This mailbox will be an in-memory one
- Option for other kinds of mailbox

## Actor Logging, Monitoring and Metrics
- Option to add a preprocessing and post processing hooks
- This can be in the form of a function that is called
- used for logging, metrics and monitoring of actors

## Actor persistence
- This functionality allow the persisting of each actors state in a preferred data store
- This will facilitate actor recovery from restarts and crashes or during system migration adding a new level of resiliance
- Actors mailbox will be stored in the chosen data store with full CRUD operations to update the mailbox implemented
- Choice of data stores to include Cassandra, Postgresql and MongoDB.

## Message Stashing
- Ability to pause processing messages of a given type in the mailbox
- Once Supervisor told to start stashing messages of that type placed in another mailbox
- Once supervisor told to stop stashing, messages in the secondary mailbox moved back to main mailbox to continue processing
- Message TYPE used to determine what to stash or unstash

## Message Routing / Actor Registry
We need a mechism to know what actors we have, which message types each actor
receives and which actor it needs to send its result to.

## General Design considerations
- Support protobuffer and avro message types
- Should we use Kafka for logging events? 

# Competition
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

# Library Documentation
Coming soon !

# Current Project Contributors
[Current Team](https://github.com/scalaz/scalaz-actors/wiki/Team)

## Contributing
[Documentation for contributors](CONTRIBUTING.md)

