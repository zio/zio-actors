package zio.actors.persistence.journal

import zio.Task
import zio.actors.persistence.PersistenceId.PersistenceId

private[actors] trait Journal[Ev] {

  def persistEvent(persistenceId: PersistenceId, event: Ev): Task[Unit]

  def getEvents(persistenceId: PersistenceId): Task[Seq[Ev]]

}

trait JournalFactory {
  def getJournal[Ev](actorSystemName: String, configStr: String): Task[Journal[Ev]]
}
