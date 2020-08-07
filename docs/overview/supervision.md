---
id: overview_supervision
title: "Supervision"
---

A `Supervisors` responsibility is to manage actors failure policies.

`ZIO` provides us a comprehensive and composable set of  `Schedules` that can be used to define complex supervising policy.

There are three supervision scenarios available. Let's look at short examples.
First do the imports:

```scala mdoc:silent
import zio.actors._
import zio.{ Supervisor => _, _ }
import zio.console._
import zio.duration._
import java.util.concurrent.TimeUnit
```

To provide no supervision use `none`. Remember that this provide no error recovery for actor:

```scala mdoc:silent
Supervisor.none
```

Retrying is provided via `retry` with specified `Schedule` like `recurs`:

```scala mdoc:silent
Supervisor.retry(Schedule.recurs(10))
```

The general method also requires effect that will be executed on `Schedule` end:

```scala mdoc:silent
Supervisor.retryOrElse[Any, Long](Schedule.recurs(10), (e, a) => putStrLn("nothing can be done").provideLayer(Console.live))
```
