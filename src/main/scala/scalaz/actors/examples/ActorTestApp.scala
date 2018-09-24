package scalaz.actors.examples

import scalaz._
import Scalaz._
import scalaz.actors.Actors._
import scalaz.zio.console.putStrLn
import scalaz.zio.{ App, IO, Schedule }

object ActorTestApp extends App {

  def makeCounterActor: IO[Nothing, Actor[Nothing, Int, Int]] =
    makeActor[Int, Nothing, Int, Int](Supervisor.retry(Schedule.never))(0) {
      (counter: Int, n: Int) =>
        IO.point((counter + n, counter + n))
    }

  override def run(args: List[String]): IO[Nothing, ExitStatus] =
    (for {
      counter <- makeCounterActor
      res     <- counter ! 2
      _       <- putStrLn(res.shows)
      res2    <- counter ! 10
      _       <- putStrLn(res2.shows)
    } yield ()).attempt
      .map(_.fold(_ => 1, _ => 0))
      .map(ExitStatus.ExitNow(_))
}
