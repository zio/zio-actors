package zio.actors.persistence.journal

import zio.Task

private[actors] trait Journal[Ev] {

  def persistEvent(persistenceId: String, event: Ev): Task[Unit]

  def getEvents(persistenceId: String): Task[Seq[Ev]]

}
