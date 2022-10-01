package zio.actors.persistence

import zio.actors.ActorsConfig._
import zio.config.ConfigDescriptor
import zio.config.ConfigDescriptor._
import zio.{ Promise, Runtime, Task, Unsafe, ZIO }

private[actors] object PersistenceConfig {

  private lazy val runtime = Runtime.default
  private lazy val promise = Unsafe.unsafe { implicit u =>
    runtime.unsafe.run(Promise.make[Exception, String]).getOrThrowFiberFailure()
  }

  final case class JournalPluginRaw(value: String)   extends AnyVal
  final case class JournalPluginClass(value: String) extends AnyVal

  final case class InMemConfig(key: String) extends AnyVal

  val pluginConfig: ConfigDescriptor[JournalPluginRaw] =
    nested("persistence") {
      string("plugin").transform(JournalPluginRaw, _.value)
    }

  def classPathConfig(pluginClass: String): ConfigDescriptor[JournalPluginClass] =
    nested("persistence") {
      nested("datastores") {
        string(pluginClass).transform(JournalPluginClass, _.value)
      }
    }

  val inMemConfig: ConfigDescriptor[InMemConfig] =
    nested("persistence") {
      string("key").transform(InMemConfig, _.key)
    }

  def getPluginClass(systemName: String, configStr: String): Task[JournalPluginClass] =
    zio.actors.ActorsConfig.getConfig(systemName, configStr, pluginConfig).flatMap(getPluginClassMapping)

  def getPluginClassMapping(journalPluginRaw: JournalPluginRaw): Task[JournalPluginClass] =
    for {
      p           <- promise.poll
      configStr   <- p match {
                       case Some(value) =>
                         value
                       case None        =>
                         for {
                           inputStream <- ZIO.attempt(getClass.getResourceAsStream("/datastores.conf"))
                           source      <- ZIO.attempt(scala.io.Source.fromInputStream(inputStream))
                           str         <- ZIO.scoped {
                                            ZIO
                                              .acquireRelease(ZIO.succeed(source))(s => ZIO.succeed(s.close()))
                                              .flatMap(s => ZIO.attempt(s.mkString))
                                          }
                           _           <- promise.succeed(str)
                         } yield str
                     }
      pluginClass <- getConfig("internals", configStr, classPathConfig(journalPluginRaw.value))
    } yield pluginClass

  def getInMemConfig(systemName: String, configStr: String): Task[InMemConfig] =
    getConfig(systemName, configStr, inMemConfig)

}
