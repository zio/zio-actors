package zio.actors.config

final case class Addr(value: String) extends AnyVal

final case class Port(value: Int) extends AnyVal

final case class RemoteConfig(addr: Addr, port: Port)
