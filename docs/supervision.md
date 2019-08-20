---
id: supervision
title: "Supervision"
---

A Supervisors responsibility is to manage actors.

- Makes sure all messages in the queue are processed
- Has retry logic to manage failures
- Takes the next available message and start actor to process it
- Puts failed actors back on the queue for reprocessing whilst respecting retry preferences

There are three supervision scenarios available. Let's look at short examples.
First do the imports:

```scala mdoc:silent
import zio.actors._
import zio._
import zio.console._
import zio.duration._
import java.util.concurrent.TimeUnit
```

To provide no supervision use `none`. Remember that this provide no error recovery for actor:

```scala mdoc:silent
Supervisor.none
```

Retrying is provided via `retry` with specified `Schedule` like `linear` or `exponential.`:

```scala mdoc:silent
val duration  = Duration(5, TimeUnit.SECONDS)

Supervisor.retry(Schedule.exponential(duration, factor = 4))
```

The general method also requires effect that will be executed on `Schedule` end:

```scala mdoc:silent
Supervisor.retryOrElse[Exception, Int](Schedule.recurs(10), (e, a) => putStrLn("nothing can be done").provide(Console.Live))
```