---
id: overview_persistence
title: "Persistence"
---

Persistence gives you ability to store events that occur in your system with defined datastore. 

To use Persistence you need in your `build.sbt`:

```scala mdoc:passthrough

println(s"""```""")
if (zio.actors.BuildInfo.isSnapshot)
  println(s"""resolvers += Resolver.sonatypeRepo("snapshots")""")
println(s"""libraryDependencies += "dev.zio" %% "zio-actors-persistence" % "${zio.actors.BuildInfo.version}"""")
println(s"""```""")

```

For current version the only datastore available is `postgresql` and in-memory datastore for testing purposes. 
For `postgresql` you need a configuration in (by default) `resources/application.conf`:

```hocon
ActorSystemName.zio.actors.persistence {
  plugin = "jdbc-journal"
  url = "jdbc:postgresql://localhost:5432/postgres"
  user = "user"
  pass = "pass"
}
```

and also use `postgresql` plugin for that purpose:

```scala mdoc:passthrough

println(s"""```""")
if (zio.actors.BuildInfo.isSnapshot)
  println(s"""resolvers += Resolver.sonatypeRepo("snapshots")""")
println(s"""libraryDependencies += "dev.zio" %% "zio-actors-persistence-jdbc" % "${zio.actors.BuildInfo.version}"""")
println(s"""```""")

```

Currently the table that needs to be present in database has such schema:

```sql
create table if not exists journal_zio
(
	persistence_id varchar not null,
	sequence_number serial not null,
	message bytea,
	constraint journal_zio_pk
		primary key (persistence_id, sequence_number)
);
```

After successful setup you can create persisted actors by implementing `EventSourcedStateful`.
First method is `receive` which is similar to `receive` from basic actors: Here you can perform an 
effectful computations with possible failures and side effects. Here you must also decide whether
processed message should result in an event that will be persisted or no state update. 
 
The second method is `sourceEvent` which must be a pure function that performs state updates.
This method is used when restoring an actor after startup. 

The imports we need for simple example:

```scala mdoc:silent
import zio.actors._
import zio.actors.{ ActorSystem, Context, Supervisor }
import zio.actors.persistence._
import zio.UIO
```

Case objects for messages that our actor can process and persisted events:

```scala mdoc:silent
sealed trait Message[+_]
case object Reset    extends Message[Unit]
case object Increase extends Message[Unit]
case object Get      extends Message[Int]

sealed trait CounterEvent
case object ResetEvent    extends CounterEvent
case object IncreaseEvent extends CounterEvent
```

`EventSourcedStateful` implementation with persisted and idempotent receive patterns:

```scala mdoc:silent
  val ESCounterHandler = new EventSourcedStateful[Any, Int, Message, CounterEvent](PersistenceId("id1")) {
    override def receive[A](
      state: Int,
      msg: Message[A],
      context: Context
    ): UIO[(Command[CounterEvent], Int => A)] =
      msg match {
        case Reset    => UIO((Command.persist(ResetEvent), _ => ()))
        case Increase => UIO((Command.persist(IncreaseEvent), _ => ()))
        case Get      => UIO((Command.ignore, _ => state))
      }

    override def sourceEvent(state: Int, event: CounterEvent): Int =
      event match {
        case ResetEvent    => 0
        case IncreaseEvent => state + 1
      }
  }
```

After defining datastore configuration and actor's behavior we can firmly stop an actor, respawn it
and expect it's state to be restored to the last event:

```scala mdoc:silent
for {
  actorSystem <- ActorSystem("testSystem1")
  actor       <- actorSystem.make("actor1", Supervisor.none, 0, ESCounterHandler)
  _           <- actor ! Increase
  _           <- actor ? Increase
  _           <- actor.stop
  actor       <- actorSystem.make("actor1", Supervisor.none, 0, ESCounterHandler)
  _           <- actor ! Increase
  counter     <- actor ? Get
} yield counter == 3
```
