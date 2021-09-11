package zio.actors.persistence.cassandra

import palanga.zio.cassandra.ZCqlSession
import palanga.zio.cassandra.session.ZCqlSession
import zio.actors.ActorSystemUtils
import zio.actors.persistence.PersistenceId.PersistenceId
import zio.actors.persistence.cassandra.CassandraConfig.DbConfig
import zio.actors.persistence.journal.{Journal, JournalFactory}
import zio.{Runtime, Task, ZIO}

private[actors] final class CassandraJournal[Ev](session: ZCqlSession.Service) extends Journal[Ev] {

  override def persistEvent(persistenceId: PersistenceId, event: Ev): Task[Unit] = {
    for {
      bytes <- ActorSystemUtils.objToByteArray(event)
      _     <- session.execute(CqlEvents.persistEvent(persistenceId, bytes))
    } yield ()
  }

  override def getEvents(persistenceId: PersistenceId): Task[Seq[Ev]] =
    session.stream(CqlEvents.getEventsById(persistenceId))
      .flattenChunks
      .mapM(ev => ActorSystemUtils.objFromByteArray(ev.bytes).map(_.asInstanceOf[Ev]))
      .runCollect
}

object CassandraJournal extends JournalFactory {

  private lazy val runtime = Runtime.default

  override def getJournal[Ev](actorSystemName: String, configStr: String): Task[CassandraJournal[Ev]] = {
    val ret = for {
      dbConfig <- CassandraConfig.getDbConfig(actorSystemName, configStr)
      session <- ZIO.access[ZCqlSession](_.get).provideCustomLayer(getSession(dbConfig))
    } yield new CassandraJournal[Ev](session)
    Task.succeed(runtime.unsafeRun(ret))
  }

  private def getSession(dbConfig: DbConfig) =
    palanga.zio.cassandra.session.layer
      .from(
        dbConfig.host.value,
        dbConfig.port.value,
        dbConfig.keyspace.value
      )
}
