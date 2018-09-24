package scalaz.actors.examples

import scalaz.Scalaz._
import scalaz.actors.Actors._
import scalaz.zio.console.putStrLn
import scalaz.zio.{ App, IO }

object ActorTestApp extends App {

  sealed trait Counter[+ _]
  case class Add(n: Int) extends Counter[Unit]
  case object Get        extends Counter[Int]
  case object Print      extends Counter[String]

  val handler: MessageHandler[Int, Nothing, Counter] = new MessageHandler[Int, Nothing, Counter] {
    override def receive[A](state: Int, msg: Counter[A]): IO[Nothing, (Int, A)] = msg match {
      case Add(n) => IO.point((state + n, ()))
      case Get    => IO.point((state, state))
      case Print  => IO.point((state, state.shows))
    }
  }

  override def run(args: List[String]): IO[Nothing, ExitStatus] =
    (for {
      counter <- makeActor(0)(handler)(Supervisor.none)
      res     <- counter ! Get
      _       <- putStrLn(res.shows)
      _       <- counter ! Add(1)
      res2    <- counter ! Print
      _       <- putStrLn(res2)
    } yield ()).attempt
      .map(_.fold(_ => 1, _ => 0))
      .map(ExitStatus.ExitNow(_))
}
