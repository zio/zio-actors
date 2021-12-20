package zio.actors.persistence.cassandra

import palanga.zio.cassandra.ZStatement.StringOps

import zio.actors.persistence.PersistenceId.PersistenceId
import palanga.zio.cassandra.ZBoundStatement

private[actors] object CqlEvents {
  final case class Event(persistenceId: PersistenceId, bytes: Array[Byte])

  def getEventsById[Ev](persistenceId: PersistenceId): ZBoundStatement[CqlEvents.Event] = {
     "SELECT message FROM journal_zio WHERE persistence_id = ? ORDER BY sequence_number"
       .toStatement
       .bind(persistenceId.value)
       .decode(row => Event(
                 PersistenceId(row.getString("persistence_id")),
                 row.getByteBuffer("message").array()
               )
       )
  }

  def persistEvent[Ev](persistenceId: PersistenceId, event: Array[Byte]): ZBoundStatement[CqlEvents.Event] =
    "INSERT INTO journal_zio (persistence_id, message) VALUES (?, ?)"
      .toStatement
      .bind(persistenceId.value, event)
      .decode(row => Event(
                 PersistenceId(row.getString("persistence_id")),
                 row.getByteBuffer("message").array()
               )
       )

}
