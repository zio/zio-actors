package zio.actors.persistence

import cats.effect.Blocker
import com.zaxxer.hikari.HikariDataSource
import zio._
import doobie._
import doobie.hikari.HikariTransactor
import doobie.implicits._
import zio.actors.ActorSystemUtils
import zio.actors.persistence.Utils.DbConfig
import zio.blocking.Blocking
import zio.interop.catz._

private[actors] trait Journal[Ev] {

  def persistEvent(persistenceId: Int, event: Ev): Task[Unit]

  def getEvents(persistenceId: Int): Task[Seq[Ev]]

}

private[actors] object InMemJournal {

  private lazy val runtime = new DefaultRuntime {}
  lazy val journalMap = {
    val journalEff =
      for {
        j <- Ref.make(Map.empty[Int, InMemJournal[_]])
        _ <- j.set(Map.empty)
      } yield j
    runtime.unsafeRun(journalEff)
  }

  def getJournal[Ev](idx: Int): UIO[InMemJournal[Ev]] =
    for {
      map <- journalMap.get
      r <- map.get(idx) match {
            case Some(value) =>
              UIO.effectTotal(value.asInstanceOf[InMemJournal[Ev]])
            case None =>
              for {
                j <- InMemJournal[Ev]()
                _ <- journalMap.set(map + (idx -> j))
              } yield j
          }
    } yield r

  def apply[Ev](): UIO[InMemJournal[Ev]] =
    for {
      ref <- Ref.make(List.empty[JournalRow[Ev]])
    } yield new InMemJournal[Ev](ref)

}

case class JournalRow[Ev](persistenceId: Int, seqNum: Int, event: Ev)

private[actors] class InMemJournal[Ev](journalRef: Ref[List[JournalRow[Ev]]]) extends Journal[Ev] {

  override def persistEvent(persistenceId: Int, event: Ev): Task[Unit] =
    for {
      journal <- journalRef.get
      maxSeq  = journal.filter(_.persistenceId == persistenceId).map(_.seqNum)
      mm      = if (maxSeq.isEmpty) 0 else maxSeq.max
      _       <- journalRef.set(journal :+ JournalRow(persistenceId, mm + 1, event))
    } yield ()

  override def getEvents(persistenceId: Int): Task[Seq[Ev]] =
    for {
      journal <- journalRef.get
      events  = journal.filter(_.persistenceId == persistenceId).sortBy(_.seqNum).map(_.event)
    } yield events

}

private[actors] object JDBCJournal {

  private lazy val runtime = new DefaultRuntime {}
  lazy val transactorPromise = runtime.unsafeRun(Promise.make[Exception, HikariTransactor[Task]])

  def constructTransactor(dbConfig: DbConfig) =
    ZIO.runtime[Blocking].flatMap { implicit rt =>
      for {
        transactEC <- rt.environment.blocking.blockingExecutor.map(_.asEC)
        connectEC = rt.platform.executor.asEC
        _ <- zio.console.Console.Live.console.putStrLn("XDDDD")
        ds = new HikariDataSource()
        _ <- IO.effect(Class.forName("org.postgresql.Driver"))
        _ = ds.setJdbcUrl(dbConfig.dbURL.value)
        _ = ds.setUsername(dbConfig.dbUser.value)
        _ = ds.setPassword(dbConfig.dbPass.value)
        transactor <- IO.effect(HikariTransactor.apply[Task](ds, connectEC, Blocker.liftExecutionContext(transactEC)))
      } yield transactor
    }

  def transactor(dbConfig: DbConfig): Task[HikariTransactor[Task]] =
    for {
      opt <- transactorPromise.poll
      tran <- opt match {
        case Some(value) =>
          value
        case None =>
          for {
            tnx <- constructTransactor(dbConfig).provide(Blocking.Live)
            _ <- transactorPromise.succeed(tnx)
          } yield tnx
      }
    } yield tran

  def getJournal[Ev](dbConfig: DbConfig): Task[JDBCJournal[Ev]] =
    for {
      tnx <- transactor(dbConfig)
    } yield new JDBCJournal[Ev](tnx)

}

private[actors] class JDBCJournal[Ev](tnx: Transactor[Task]) extends Journal[Ev] {

  override def persistEvent(persistenceId: Int, event: Ev): Task[Unit] =
    for {
      bytes <- ActorSystemUtils.objToByteArray(event)
      _     <- SQL.persistEvent(persistenceId, bytes).run.transact(tnx)
    } yield ()


  override def getEvents(persistenceId: Int): Task[Seq[Ev]] =
    for {
      bytes  <- SQL.getEventsById(persistenceId).to[Seq].transact(tnx)
      events <- IO.sequence(bytes.map(ActorSystemUtils.objFromByteArray(_).map(_.asInstanceOf[Ev])))
    } yield events

}

object SQL {
  def getEventsById[Ev](persistenceId: Int): Query0[Array[Byte]] =
    sql"""SELECT message FROM journal_zio WHERE persistence_id = $persistenceId""".query[Array[Byte]]

  def persistEvent[Ev](persistenceId: Int, event: Array[Byte]): Update0 =
    sql"""INSERT INTO journal_zio (persistence_id, message) VALUES ($persistenceId, $event)""".update
}
