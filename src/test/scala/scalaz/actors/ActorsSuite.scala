package scalaz.actors

import testz.{ Harness, assert }

final class ActorsSuite {

  def tests[A](harness: Harness[A]): A = {
    import harness._

    section {
      test("Sanity check") { () =>
        assert(1 + 1 == 2)
      }
    }
  }
}
