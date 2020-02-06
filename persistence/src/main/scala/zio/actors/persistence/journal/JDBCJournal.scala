package zio.actors.persistence.journal

import cats.effect.Blocker
import com.zaxxer.hikari.HikariDataSource
import doobie._
import doobie.hikari.HikariTransactor
import doobie.implicits._

import zio.{ DefaultRuntime, IO, Promise, Task, ZIO }
import zio.actors.ActorSystemUtils
import zio.actors.persistence.Utils.DbConfig
import zio.blocking.Blocking
import zio.interop.catz._

private[actors] object JDBCJournal {

  private lazy val runtime   = new DefaultRuntime {}
  lazy val transactorPromise = runtime.unsafeRun(Promise.make[Exception, HikariTransactor[Task]])

  def constructTransactor(dbConfig: DbConfig): ZIO[Blocking, Throwable, HikariTransactor[Task]] =
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
    for {
      optTnx <- transactorPromise.poll
      tnx <- optTnx match {
              case Some(value) =>
                value
              case None =>
                for {
                  newTnx <- constructTransactor(dbConfig).provide(Blocking.Live)
                  _      <- transactorPromise.succeed(newTnx)
                } yield newTnx
            }
    } yield tnx

  def getJournal[Ev](dbConfig: DbConfig): Task[JDBCJournal[Ev]] =
    for {
      tnx <- transactor(dbConfig)
    } yield new JDBCJournal[Ev](tnx)

}

private[actors] class JDBCJournal[Ev](tnx: Transactor[Task]) extends Journal[Ev] {

  override def persistEvent(persistenceId: String, event: Ev): Task[Unit] =
    for {
      bytes <- ActorSystemUtils.objToByteArray(event)
      _     <- SQL.persistEvent(persistenceId, bytes).run.transact(tnx)
    } yield ()

  override def getEvents(persistenceId: String): Task[Seq[Ev]] =
    for {
      bytes  <- SQL.getEventsById(persistenceId).to[Seq].transact(tnx)
      events <- IO.sequence(bytes.map(ActorSystemUtils.objFromByteArray(_).map(_.asInstanceOf[Ev])))
    } yield events

}

private[actors] object SQL {
  def getEventsById[Ev](persistenceId: String): Query0[Array[Byte]] =
    sql"""SELECT message FROM journal_zio WHERE persistence_id = $persistenceId ORDER BY sequence_number"""
      .query[Array[Byte]]

  def persistEvent[Ev](persistenceId: String, event: Array[Byte]): Update0 =
    sql"""INSERT INTO journal_zio (persistence_id, message) VALUES ($persistenceId, $event)""".update
}
