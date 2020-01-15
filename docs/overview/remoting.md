---
id: overview_remoting
title: "Remoting"
---

Remoting makes it possible to lookup for actors that reside on another `ActorSystems`.
The configuration is done by providing address and port in configuration file 
([see Configuration section](basics.md#configuration)) that the `ActorSystem` will be bound to:

```scala mdoc:silent
import zio.actors._

for {
  _ <- ActorSystem("mySystem")
} yield ()
```

Then we can `select` actors from another ActorSystem and send messages thanks to underlying socket communication.

## Serialization

Currently serialization is done via Java Serialization. 
User defined messages are serialized "as is" except for `ActorRefs`, 
which are serialized into actor's absolute path and deserialized into a remote `ActorRef` pointing to the original one:

![diagram](../assets/remote.svg)
