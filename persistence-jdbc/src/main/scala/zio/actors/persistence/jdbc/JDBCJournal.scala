package zio.actors.persistence.jdbc

import cats.effect.Blocker
import com.zaxxer.hikari.HikariDataSource
import doobie._
import doobie.hikari.HikariTransactor
import doobie.implicits._

import zio.{ DefaultRuntime, IO, Promise, Task, ZIO }
import zio.actors.ActorSystemUtils
import zio.actors.persistence.PersistenceId.PersistenceId
import zio.actors.persistence.journal.Journal
import zio.actors.persistence.jdbc.JDBCConfig.DbConfig
import zio.blocking.Blocking
import zio.interop.catz._

private[actors] object JDBCJournal {

  private lazy val runtime           = new DefaultRuntime {}
  private lazy val transactorPromise = runtime.unsafeRun(Promise.make[Exception, HikariTransactor[Task]])

  def makeTransactor(dbConfig: DbConfig): ZIO[Blocking, Throwable, HikariTransactor[Task]] =
    ZIO.runtime[Blocking].flatMap { implicit rt =>
      for {
        transactEC <- rt.environment.blocking.blockingExecutor.map(_.asEC)
        connectEC  = rt.platform.executor.asEC
        ds         = new HikariDataSource()
        _          <- IO.effect(Class.forName("org.postgresql.Driver"))
        _          = ds.setJdbcUrl(dbConfig.dbURL.value)
        _          = ds.setUsername(dbConfig.dbUser.value)
        _          = ds.setPassword(dbConfig.dbPass.value)
        transactor <- IO.effect(HikariTransactor.apply[Task](ds, connectEC, Blocker.liftExecutionContext(transactEC)))
      } yield transactor
    }

  def transactor(dbConfig: DbConfig): Task[HikariTransactor[Task]] =
    transactorPromise.poll.flatMap {
      case Some(value) => value
      case None =>
        for {
          newTnx <- makeTransactor(dbConfig).provide(Blocking.Live)
          _      <- transactorPromise.succeed(newTnx)
        } yield newTnx
    }

  def getJournal[Ev](actorSystemName: String, configStr: String): Task[JDBCJournal[Ev]] =
    for {
      dbConfig <- JDBCConfig.getDbConfig(actorSystemName, configStr)
      tnx      <- transactor(dbConfig)
    } yield new JDBCJournal[Ev](tnx)

}

private[actors] class JDBCJournal[Ev](tnx: Transactor[Task]) extends Journal[Ev] {

  override def persistEvent(persistenceId: PersistenceId, event: Ev): Task[Unit] =
    for {
      bytes <- ActorSystemUtils.objToByteArray(event)
      _     <- SQL.persistEvent(persistenceId, bytes).run.transact(tnx)
    } yield ()

  override def getEvents(persistenceId: PersistenceId): Task[Seq[Ev]] =
    for {
      bytes  <- SQL.getEventsById(persistenceId).to[Seq].transact(tnx)
      events <- IO.sequence(bytes.map(ActorSystemUtils.objFromByteArray(_).map(_.asInstanceOf[Ev])))
    } yield events

}

private[actors] object SQL {
  def getEventsById[Ev](persistenceId: PersistenceId): Query0[Array[Byte]] =
    sql"""SELECT message FROM journal_zio WHERE persistence_id = ${persistenceId.value} ORDER BY sequence_number"""
      .query[Array[Byte]]

  def persistEvent[Ev](persistenceId: PersistenceId, event: Array[Byte]): Update0 =
    sql"""INSERT INTO journal_zio (persistence_id, message) VALUES (${persistenceId.value}, $event)""".update
}
