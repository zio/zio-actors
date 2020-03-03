package zio.actors.persistence.journal

import zio.{ Ref, Runtime, Task, UIO }
import zio.actors.persistence.PersistenceId.PersistenceId
import zio.actors.persistence.PersistenceConfig
import InMemJournal.JournalRow

private[actors] final class InMemJournal[Ev](journalRef: Ref[List[JournalRow[Ev]]]) extends Journal[Ev] {

  override def persistEvent(persistenceId: PersistenceId, event: Ev): Task[Unit] =
    for {
      journal <- journalRef.get
      maxSeq  = journal.collect { case row if row.persistenceId == persistenceId => row.seqNum }
      max     = if (maxSeq.isEmpty) 0 else maxSeq.max
      _       <- journalRef.set(journal :+ JournalRow(persistenceId, max + 1, event))
    } yield ()

  override def getEvents(persistenceId: PersistenceId): Task[Seq[Ev]] =
    for {
      journal <- journalRef.get
      events  = journal.filter(_.persistenceId == persistenceId).sortBy(_.seqNum).map(_.event)
    } yield events

}

private[actors] object InMemJournal {

  private case class JournalRow[Ev](persistenceId: PersistenceId, seqNum: Int, event: Ev)

  private lazy val runtime = Runtime.default
  lazy val journalMap = {
    val journalEff =
      for {
        j <- Ref.make(Map.empty[String, InMemJournal[_]])
        _ <- j.set(Map.empty)
      } yield j
    runtime.unsafeRun(journalEff)
  }

  def getJournal[Ev](actorSystemName: String, configStr: String): Task[InMemJournal[Ev]] =
    for {
      inMemConfig <- PersistenceConfig.getInMemConfig(actorSystemName, configStr)
      key         = inMemConfig.key
      map         <- journalMap.get
      journal <- map.get(key) match {
                  case Some(j) =>
                    UIO.effectTotal(j.asInstanceOf[InMemJournal[Ev]])
                  case None =>
                    for {
                      j <- InMemJournal.make[Ev]()
                      _ <- journalMap.set(map + (key -> j))
                    } yield j
                }
    } yield journal

  def make[Ev](): UIO[InMemJournal[Ev]] =
    Ref.make(List.empty[JournalRow[Ev]]).map(new InMemJournal[Ev](_))

}
