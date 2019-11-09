---
id: overview
title: "Overview"
---

The basic actors usage requires defining a `Stateful` for describing actor's behavior.
Then actor's creation is done with passing supervision manner, initial state and mentioned `Stateful`.

Imports required for example:

```scala mdoc:silent
import zio.actors.Actor.Stateful
import zio.actors._
import zio.IO
```

Our domain that will be used:

```scala mdoc:silent
sealed trait Command[+_]
case class DoubleCommand(value: Int) extends Command[Int]
```

Our actor's assigment will be to double received values. Here's the `Stateful` implementation:

```scala mdoc:silent
val stateful = new Stateful[Unit, Throwable, Command] {
  override def receive[A](state: Unit, msg: Command[A], context: Context[Throwable, Command]): IO[Throwable, (Unit, A)] =
    msg match {
      case DoubleCommand(value) => IO.effectTotal(((), value * 2))
    }
}
```

Then we are ready to instantiate the actor and fire off messages:

```scala mdoc:silent
for {
  system <- ActorSystem("mySystem", remoteConfig = None)
  actor <- system.createActor("actor1", Supervisor.none, (), stateful)
  doubled <- actor ! DoubleCommand(42)
} yield doubled
```
