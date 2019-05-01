package zio.actors

import testz.PureHarness

object Main {

  def main(args: Array[String]): Unit = {
    val harness = PureHarness.makeFromPrinter { (result, name) =>
      println(s"${name.reverse.mkString}: $result")
    }

    val result = new ActorsSuite().tests(harness)((), Nil)

    result.print()
    if (result.failed) throw new Exception("some tests failed")
  }
}
