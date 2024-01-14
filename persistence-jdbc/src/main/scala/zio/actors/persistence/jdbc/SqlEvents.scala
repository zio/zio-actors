package zio.actors.persistence.jdbc

import doobie.{ Query0, Update0 }
import doobie.implicits._

import zio.actors.persistence.PersistenceId.PersistenceId

private[actors] object SqlEvents {
  def getEventsById[Ev](persistenceId: PersistenceId): Query0[Array[Byte]] =
    sql"""SELECT message FROM journal_zio WHERE persistence_id = ${persistenceId.value} ORDER BY sequence_number"""
      .query[Array[Byte]]

  def persistEvent[Ev](persistenceId: PersistenceId, event: Array[Byte]): Update0 =
    sql"""INSERT INTO journal_zio (persistence_id, message) VALUES (${persistenceId.value}, $event)""".update
}
