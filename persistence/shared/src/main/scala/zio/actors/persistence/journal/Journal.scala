package zio.actors.persistence.journal

import com.typesafe.config.Config
import org.portablescala.reflect.annotation.EnableReflectiveInstantiation
import zio.Task
import zio.actors.persistence.PersistenceId.PersistenceId

private[actors] trait Journal[Ev] {

  def persistEvent(persistenceId: PersistenceId, event: Ev): Task[Unit]

  def getEvents(persistenceId: PersistenceId): Task[Seq[Ev]]

}

@EnableReflectiveInstantiation
trait JournalFactory {
  def getJournal[Ev](actorSystemName: String, config: Config): Task[Journal[Ev]]
}
