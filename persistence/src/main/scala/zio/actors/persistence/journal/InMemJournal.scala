package zio.actors.persistence.journal

import zio.{ DefaultRuntime, Ref, Task, UIO }
import InMemJournal.JournalRow

private[actors] object InMemJournal {

  private case class JournalRow[Ev](persistenceId: String, seqNum: Int, event: Ev)

  private lazy val runtime = new DefaultRuntime {}
  lazy val journalMap = {
    val journalEff =
      for {
        j <- Ref.make(Map.empty[String, InMemJournal[_]])
        _ <- j.set(Map.empty)
      } yield j
    runtime.unsafeRun(journalEff)
  }

  def getJournal[Ev](key: String): UIO[InMemJournal[Ev]] =
    for {
      map <- journalMap.get
      journal <- map.get(key) match {
                  case Some(j) =>
                    UIO.effectTotal(j.asInstanceOf[InMemJournal[Ev]])
                  case None =>
                    for {
                      j <- InMemJournal[Ev]()
                      _ <- journalMap.set(map + (key -> j))
                    } yield j
                }
    } yield journal

  def apply[Ev](): UIO[InMemJournal[Ev]] =
    for {
      rows <- Ref.make(List.empty[JournalRow[Ev]])
    } yield new InMemJournal[Ev](rows)

}

private[actors] class InMemJournal[Ev](journalRef: Ref[List[JournalRow[Ev]]]) extends Journal[Ev] {

  override def persistEvent(persistenceId: String, event: Ev): Task[Unit] =
    for {
      journal <- journalRef.get
      maxSeq  = journal.filter(_.persistenceId == persistenceId).map(_.seqNum)
      max     = if (maxSeq.isEmpty) 0 else maxSeq.max
      _       <- journalRef.set(journal :+ JournalRow(persistenceId, max + 1, event))
    } yield ()

  override def getEvents(persistenceId: String): Task[Seq[Ev]] =
    for {
      journal <- journalRef.get
      events  = journal.filter(_.persistenceId == persistenceId).sortBy(_.seqNum).map(_.event)
    } yield events

}
