package zio.actors.persistence.jdbc

import com.zaxxer.hikari.HikariDataSource
import doobie._
import doobie.hikari.HikariTransactor
import doobie.implicits._
import zio._
import zio.actors.ActorSystemUtils
import zio.actors.persistence.PersistenceId.PersistenceId
import zio.actors.persistence.journal.{ Journal, JournalFactory }
import zio.actors.persistence.jdbc.JDBCConfig.DbConfig
import zio.interop.catz._
import zio.internal.Blocking

private[actors] final class JDBCJournal[Ev](tnx: Transactor[Task]) extends Journal[Ev] {

  override def persistEvent(persistenceId: PersistenceId, event: Ev): Task[Unit] =
    for {
      bytes <- ActorSystemUtils.objToByteArray(event)
      _     <- SqlEvents.persistEvent(persistenceId, bytes).run.transact(tnx)
    } yield ()

  override def getEvents(persistenceId: PersistenceId): Task[Seq[Ev]] =
    for {
      bytes  <- SqlEvents.getEventsById(persistenceId).to[Seq].transact(tnx)
      events <- ZIO.collectAll(bytes.map(ActorSystemUtils.objFromByteArray(_).map(_.asInstanceOf[Ev])))
    } yield events

}

object JDBCJournal extends JournalFactory {

  private lazy val transactorPromise = Unsafe.unsafe { implicit runtime =>
    Runtime.default.unsafe.run(Promise.make[Exception, HikariTransactor[Task]]).getOrThrowFiberFailure()
  }

  def getJournal[Ev](actorSystemName: String, configStr: String): Task[JDBCJournal[Ev]] =
    for {
      dbConfig <- JDBCConfig.getDbConfig(actorSystemName, configStr)
      tnx      <- getTransactor(dbConfig)
    } yield new JDBCJournal[Ev](tnx)

  private def makeTransactor(dbConfig: DbConfig): ZIO[Any, Throwable, HikariTransactor[Task]] =
    ZIO.runtime[Any].flatMap { implicit rt =>
      for {
        transactEC <- ZIO.succeed(Blocking.blockingExecutor.asExecutionContext)
        ds          = new HikariDataSource()
        _           = ds.setJdbcUrl(dbConfig.dbURL.value)
        _           = ds.setUsername(dbConfig.dbUser.value)
        _           = ds.setPassword(dbConfig.dbPass.value)
        transactor <- ZIO.attempt(HikariTransactor.apply[Task](ds, transactEC))
      } yield transactor
    }

  private def getTransactor(dbConfig: DbConfig): Task[HikariTransactor[Task]] =
    transactorPromise.poll.flatMap {
      case Some(value) => value
      case None        =>
        for {
          newTnx <- makeTransactor(dbConfig)
          _      <- transactorPromise.succeed(newTnx)
        } yield newTnx
    }

}
