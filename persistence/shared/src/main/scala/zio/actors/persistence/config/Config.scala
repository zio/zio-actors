package zio.actors.persistence.config

final case class JournalPluginRaw(value: String)   extends AnyVal
final case class JournalPluginClass(value: String) extends AnyVal
final case class InMemConfig(key: String)          extends AnyVal
