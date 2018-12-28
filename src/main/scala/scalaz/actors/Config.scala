package scalaz.actors

import ciris.enumeratum._
import ciris.{ env, prop }
import ciris.loadConfig

final case class Config(
  mailboxSize: Int
)

object ActorConfig {

  val config =
    loadConfig(
      env[Int]("MAILBOX_SIZE")
        .orElse(prop("mailbox.size")) orNone
    ) { (mSize) =>
      Config(
        mailboxSize = mSize.getOrElse(10000)
      )
    }
}
