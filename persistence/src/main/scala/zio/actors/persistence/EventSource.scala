package zio.actors.persistence

import zio.actors.Actor._
import zio.actors.persistence.PersistenceId._
import zio.actors.persistence.journal.{ Journal, JournalFactory }
import zio.actors.{ Actor, Context, Supervisor }
import zio.{ Queue, RIO, Ref, Task, ZIO }

import scala.reflect.runtime.universe

/**
 * Each message can result in either an event that will be persisted or idempotent action.
 * Changing the actor's state can only occur via `Persist` event.
 *
 * @tparam Ev events that will be persisted
 */
sealed trait Command[+Ev]
object Command {
  case class Persist[+Ev](event: Ev) extends Command[Ev]
  case object Ignore                 extends Command[Nothing]

  def persist[Ev](event: Ev): Persist[Ev] = Persist(event)
  def ignore: Ignore.type                 = Ignore
}

object PersistenceId {
  final case class PersistenceId(value: String) extends AnyVal
  def apply(value: String): PersistenceId = PersistenceId(value)
}

/**
 * Description of event sources actor's behavior
 *
 * @param persistenceId unique id used in a datastore for identifying the entity
 * @tparam S state type
 * @tparam F type of messages that actor receives
 * @tparam Ev events that will be persisted
 */
abstract class EventSourcedStateful[R, S, -F[+_], Ev](persistenceId: PersistenceId) extends AbstractStateful[R, S, F] {

  def receive[A](state: S, msg: F[A], context: Context): RIO[R, (Command[Ev], S => A)]

  def sourceEvent(state: S, event: Ev): S

  /* INTERNAL API */

  override final def makeActor(
    supervisor: Supervisor[R],
    context: Context,
    optOutActorSystem: () => Task[Unit],
    mailboxSize: Int = DefaultActorMailboxSize
  )(initial: S): RIO[R, Actor[F]] = {

    val mirror = universe.runtimeMirror(getClass.getClassLoader)

    def retrieveJournal: Task[Journal[Ev]] =
      for {
        configStr    <- ZIO
                          .fromOption(context.actorSystemConfig)
                          .orElseFail(new Exception("Couldn't retrieve persistence config"))
        systemName    = context.actorSystemName
        pluginClass  <- PersistenceConfig.getPluginClass(systemName, configStr)
        maybeFactory <-
          ZIO
            .attempt(mirror.reflectModule(mirror.staticModule(pluginClass.value)).instance)
            .mapError(e => new IllegalArgumentException(s"Could not load plugin class $pluginClass from $configStr", e))
        factory      <-
          ZIO
            .attempt(maybeFactory.asInstanceOf[JournalFactory])
            .mapError(e =>
              new IllegalArgumentException(
                s"Plugin class $maybeFactory from $configStr is not a ${classOf[JournalFactory].getCanonicalName}",
                e
              )
            )
        journal      <- factory.getJournal[Ev](systemName, configStr)
      } yield journal

    def applyEvents(events: Seq[Ev], state: S): S = events.foldLeft(state)(sourceEvent)

    def process[A](msg: PendingMessage[F, A], state: Ref[S], journal: Journal[Ev]): RIO[R, Unit] =
      for {
        s                  <- state.get
        (fa, promise)       = msg
        receiver            = receive(s, fa, context)
        effectfulCompleter  = (s: S, a: A) => state.set(s) *> promise.succeed(a)
        idempotentCompleter = (a: A) => promise.succeed(a)
        fullCompleter       = (
                                  (
                                    ev: Command[Ev],
                                    sa: S => A
                                  ) =>
                                    ev match {
                                      case Command.Ignore      => idempotentCompleter(sa(s))
                                      case Command.Persist(ev) =>
                                        for {
                                          _           <- journal.persistEvent(persistenceId, ev)
                                          updatedState = sourceEvent(s, ev)
                                          res         <- effectfulCompleter(updatedState, sa(updatedState))
                                        } yield res
                                    }
                              ).tupled
        _                  <- receiver.foldZIO(
                                e =>
                                  supervisor
                                    .supervise(receiver, e)
                                    .foldZIO(_ => promise.fail(e), fullCompleter),
                                fullCompleter
                              )
      } yield ()

    for {
      journal     <- retrieveJournal
      events      <- journal.getEvents(persistenceId)
      sourcedState = applyEvents(events, initial)
      state       <- Ref.make(sourcedState)
      queue       <- Queue.bounded[PendingMessage[F, _]](mailboxSize)
      _           <- (for {
                         t <- queue.take
                         _ <- process(t, state, journal)
                       } yield ()).forever.fork
    } yield new Actor[F](queue)(optOutActorSystem)
  }

}
