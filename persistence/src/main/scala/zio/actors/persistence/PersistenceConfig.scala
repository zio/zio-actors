package zio.actors.persistence

import zio.actors.ActorsConfig
import zio.{ IO, Managed, Promise, Runtime, Task, UIO }
import zio.config.ConfigDescriptor
import zio.config.ConfigDescriptor._
import ActorsConfig._

private[actors] object PersistenceConfig {

  private lazy val runtime = Runtime.default
  private lazy val promise = runtime.unsafeRun(Promise.make[Exception, String])

  final case class JournalPluginRaw(value: String)   extends AnyVal
  final case class JournalPluginClass(value: String) extends AnyVal

  final case class InMemConfig(key: String) extends AnyVal

  val pluginConfig: ConfigDescriptor[JournalPluginRaw] =
    nested("persistence") {
      string("plugin").xmap(JournalPluginRaw, _.value)
    }

  def classPathConfig(pluginClass: String): ConfigDescriptor[JournalPluginClass] =
    nested("persistence") {
      nested("datastores") {
        string(pluginClass).xmap(JournalPluginClass, _.value)
      }
    }

  val inMemConfig: ConfigDescriptor[InMemConfig] =
    nested("persistence") {
      string("key").xmap(InMemConfig, _.key)
    }

  def getPluginClass(systemName: String, configStr: String): Task[JournalPluginClass] =
    ActorsConfig.getConfig(systemName, configStr, pluginConfig).flatMap(getPluginClassMapping)

  def getPluginClassMapping(journalPluginRaw: JournalPluginRaw): Task[JournalPluginClass] =
    for {
      p           <- promise.poll
      configStr   <- p match {
                       case Some(value) =>
                         value
                       case None        =>
                         for {
                           inputStream <- IO(getClass.getResourceAsStream("/datastores.conf"))
                           source      <- IO(scala.io.Source.fromInputStream(inputStream))
                           str         <- Managed.make(IO(source))(s => UIO(s.close())).use(s => IO(s.mkString))
                           _           <- promise.succeed(str)
                         } yield str
                     }
      pluginClass <- getConfig("internals", configStr, classPathConfig(journalPluginRaw.value))
    } yield pluginClass

  def getInMemConfig(systemName: String, configStr: String): Task[InMemConfig] =
    getConfig(systemName, configStr, inMemConfig)

}
