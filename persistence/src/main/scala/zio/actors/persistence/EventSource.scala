package zio.actors.persistence

import java.io.File

import zio.actors._
import zio.{ IO, Queue, Ref, Task }
import zio.actors.Actor._
import zio.actors.persistence.Utils.JournalPlugin
import zio.actors.persistence.journal.{ InMemJournal, JDBCJournal, Journal }

/**
 *
 * Each message can result in either an event that will be persisted or idempotent action.
 * Changing the actor's state can only occur via `Persist` event.
 *
 * @tparam Ev events that will be persisted
 */
sealed trait Command[+Ev]
case class Persist[+Ev](event: Ev) extends Command[Ev]
case object Ignore                 extends Command[Nothing]
object Command {
  def persist[Ev](event: Ev): Persist[Ev] = Persist(event)

  def ignore: Ignore.type = Ignore
}

/**
 *
 * Description of event sources actor's behavior
 *
 * @param persistenceId unique id used in a datastore for identifying the entity
 * @tparam S state type
 * @tparam E type of errors that might be produced while processing messages
 * @tparam F type of messages that actor receives
 * @tparam Ev events that will be persisted
 */
abstract class EventSourcedStateful[S, +E <: Throwable, -F[+_], Ev](persistenceId: String)
    extends AbstractStateful[S, E, F] {

  def receive[A](state: S, msg: F[A], context: Context): IO[E, (Command[Ev], A)]

  def sourceEvent(state: S, event: Ev): S

  /* INTERNAL API */

  override final def constructActor(
    supervisor: Supervisor[E],
    context: Context,
    mailboxSize: Int = DefaultActorMailboxSize
  )(initial: S): Task[Actor[E, F]] = {

    val sysName       = context.actorSystem.actorSystemName
    val optConfigFile = context.actorSystem.configFile

    def retrieveConfig[A](f: (String, File) => Task[A]): Task[A] =
      optConfigFile.fold[Task[A]](Task.fail(new Exception("Couldn't retrieve persistence config")))(file =>
        f(sysName, file)
      )

    def retrieveJournal: Task[Journal[Ev]] =
      for {
        pluginChoice <- retrieveConfig(Utils.getPluginChoice)
        journal <- pluginChoice match {
                    case JournalPlugin("jdbc-journal") =>
                      for {
                        dbConfig <- retrieveConfig(Utils.getDbConfig)
                        j        <- JDBCJournal.getJournal[Ev](dbConfig)
                      } yield j
                    case JournalPlugin("in-mem-journal") =>
                      for {
                        inMemConfig <- retrieveConfig(Utils.getInMemConfig)
                        j           <- InMemJournal.getJournal[Ev](inMemConfig.key)
                      } yield j
                    case _ =>
                      IO.fail(new Exception("Invalid plugin config definition"))
                  }
      } yield journal

    def applyEvents(events: Seq[Ev], state: S): S = events.foldLeft(state)(sourceEvent)

    def process[A](msg: PendingMessage[E, F, A], state: Ref[S], journal: Journal[Ev]): Task[Unit] =
      for {
        s                   <- state.get
        (fa, promise)       = msg
        receiver            = receive(s, fa, context)
        effectfulCompleter  = (s: S, a: A) => state.set(s) *> promise.succeed(a)
        idempotentCompleter = (a: A) => promise.succeed(a)
        fullCompleter = (
          (
            ev: Command[Ev],
            a: A
          ) =>
            ev match {
              case Ignore => idempotentCompleter(a)
              case Persist(ev) =>
                for {
                  _            <- journal.persistEvent(persistenceId, ev)
                  updatedState = sourceEvent(s, ev)
                  res          <- effectfulCompleter(updatedState, a)
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
      journal      <- retrieveJournal
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
