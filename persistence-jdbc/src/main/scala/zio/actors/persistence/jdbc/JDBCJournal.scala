package zio.actors.persistence.jdbc

import cats.effect.Blocker
import com.zaxxer.hikari.HikariDataSource
import doobie._
import doobie.hikari.HikariTransactor
import doobie.implicits._
import zio.{ IO, Promise, Runtime, Task, UIO, ZIO }
import zio.actors.ActorSystemUtils
import zio.actors.persistence.PersistenceId.PersistenceId
import zio.actors.persistence.journal.{ Journal, JournalFactory }
import zio.actors.persistence.jdbc.JDBCConfig.DbConfig
import zio.blocking.Blocking
import zio.interop.catz._

private[actors] final class JDBCJournal[Ev](tnx: Transactor[Task]) extends Journal[Ev] {

  override def persistEvent(persistenceId: PersistenceId, event: Ev): Task[Unit] =
    for {
      bytes <- ActorSystemUtils.objToByteArray(event)
      _     <- SqlEvents.persistEvent(persistenceId, bytes).run.transact(tnx)
    } yield ()

  override def getEvents(persistenceId: PersistenceId): Task[Seq[Ev]] =
    for {
      bytes  <- SqlEvents.getEventsById(persistenceId).to[Seq].transact(tnx)
      events <- IO.collectAll(bytes.map(ActorSystemUtils.objFromByteArray(_).map(_.asInstanceOf[Ev])))
    } yield events

}

object JDBCJournal extends JournalFactory {

  private lazy val runtime           = Runtime.default
  private lazy val transactorPromise = runtime.unsafeRun(Promise.make[Exception, HikariTransactor[Task]])

  def getJournal[Ev](actorSystemName: String, configStr: String): Task[JDBCJournal[Ev]] =
    for {
      dbConfig <- JDBCConfig.getDbConfig(actorSystemName, configStr)
      tnx      <- getTransactor(dbConfig)
    } yield new JDBCJournal[Ev](tnx)

  private def makeTransactor(dbConfig: DbConfig): ZIO[Blocking, Throwable, HikariTransactor[Task]] =
    ZIO.runtime[Blocking].flatMap { implicit rt =>
      for {
        transactEC <- UIO(rt.environment.get.blockingExecutor.asEC)
        connectEC   = rt.platform.executor.asEC
        ds          = new HikariDataSource()
        _           = ds.setJdbcUrl(dbConfig.dbURL.value)
        _           = ds.setUsername(dbConfig.dbUser.value)
        _           = ds.setPassword(dbConfig.dbPass.value)
        transactor <- IO.effect(HikariTransactor.apply[Task](ds, connectEC, Blocker.liftExecutionContext(transactEC)))
      } yield transactor
    }

  private def getTransactor(dbConfig: DbConfig): Task[HikariTransactor[Task]] =
    transactorPromise.poll.flatMap {
      case Some(value) => value
      case None        =>
        for {
          newTnx <- makeTransactor(dbConfig).provideLayer(Blocking.live)
          _      <- transactorPromise.succeed(newTnx)
        } yield newTnx
    }

}
