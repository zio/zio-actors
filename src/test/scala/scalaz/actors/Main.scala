package scalaz.actors

import testz.PureHarness

object Main {

  def main(args: Array[String]): Unit = {
    val harness = PureHarness.makeFromPrinter { (result, name) =>
      println(s"${name.reverse.mkString}: $result")
    }
    new ActorsSuite().tests(harness)((), Nil).print()
  }
}
