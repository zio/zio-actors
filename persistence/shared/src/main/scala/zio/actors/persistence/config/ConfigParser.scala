package zio.actors.persistence.config

import com.typesafe.config.Config
import zio.Task

trait ConfigParser[T] {
  def parse(systemName: String, config: Config): Task[T]
}

object ConfigParser {
  implicit object InMemConfigParser extends ConfigParser[InMemConfig] {
    def parse(systemName: String, config: Config): Task[InMemConfig] =
      Task(InMemConfig(config.getString(s"$systemName.zio.actors.persistence.key")))
  }
}
