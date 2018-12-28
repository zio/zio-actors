package scalaz.actors

import scalaz.zio.RTS
import testz.{ Harness, assert }

final class ConfigSuite extends RTS {

  def tests[A](harness: Harness[A]): A = {
    import harness._

    section(
      test("fetching mailboxSize") { () =>
        val mSize: Int = ActorConfig.config.orThrow().mailboxSize
        assert(mSize == 10000)
      }
    )
  }
}
