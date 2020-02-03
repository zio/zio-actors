package zio.actors.persistence

import java.io.File

import zio.actors._
import zio.{IO, Queue, Ref, Task}
import zio.actors.Actor.{AbstractStateful, DefaultActorMailboxSize, PendingMessage}
import zio.actors.persistence.Utils.JournalPlugin

sealed trait Command[+Ev]
case class Persist[+Ev](event: Ev) extends Command[Ev]
case object Ignore                 extends Command[Nothing]
object Command {
  def persist[Ev](event: Ev) = Persist(event)

  def ignore = Ignore
}

abstract class EventSourcedStateful[S, +E <: Throwable, -F[+_], Ev](persistenceId: Int)
    extends AbstractStateful[S, E, F] {

  def receive[A](state: S, msg: F[A], context: Context): IO[E, (Command[Ev], A)]

  def sourceEvent(state: S, event: Ev): S

  override final def constructActor(
    supervisor: Supervisor[E],
    context: Context,
    mailboxSize: Int = DefaultActorMailboxSize
  )(initial: S): Task[Actor[E, F]] = {

    val sysName       = context.actorSystem.actorSystemName
    val optConfigFile = context.actorSystem.configFile

    def retrieveConfig[A](f: (String, File) => Task[A]): Task[A] =
      optConfigFile.fold[Task[A]](Task.fail(new Throwable))(file => f(sysName, file))

    def applyEvents(events: Seq[Ev], state: S): S = events.foldLeft(state)(sourceEvent)

    def process[A](msg: PendingMessage[E, F, A], state: Ref[S], journal: Journal[Ev]): Task[Unit] =
      for {
        s                   <- state.get
        (fa, promise)       = msg
        receiver            = receive(s, fa, context)
        completer           = (s: S, a: A) => state.set(s) *> promise.succeed(a)
        idempotentCompleter = (a: A) => promise.succeed(a)
        fullCompleter = (
          (
            ev: Command[Ev],
            a: A
          ) =>
            ev match {
              case Ignore      => idempotentCompleter(a)
              case Persist(ee) =>
                for {
                  _   <- journal.persistEvent(persistenceId, ee)
                  uu  = sourceEvent(s, ee)
                  res <- completer(uu, a)
                } yield res
            }
          ).tupled
        _ <- receiver.foldM(
              e =>
                supervisor
                  .supervise(receiver, e)
                  .foldM(_ => promise.fail(e), fullCompleter),
              fullCompleter
            )
      } yield ()

    for {
      pluginChoice       <- retrieveConfig(Utils.getPluginChoice)
      journal <- pluginChoice match {
        case JournalPlugin("jdbc-journal") =>
          for {
            dbConfig <- retrieveConfig(Utils.getDbConfig)
            j <- JDBCJournal.getJournal[Ev](dbConfig)
          } yield j
        case JournalPlugin("in-mem-journal") =>
          InMemJournal.getJournal[Ev](1)
        case _ =>
          IO.fail(new Throwable)
      }
      events       <- journal.getEvents(persistenceId)
      sourcedState = applyEvents(events, initial)
      state        <- Ref.make(sourcedState)
      queue        <- Queue.bounded[PendingMessage[E, F, _]](mailboxSize)
      _ <- (for {
            t <- queue.take
            _ <- process(t, state, journal)
          } yield ()).forever.fork
    } yield new Actor[E, F](queue)
  }

}
