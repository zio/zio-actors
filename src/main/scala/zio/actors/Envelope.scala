package zio.actors

private[actors] final case class Envelope(msg: Any, recipient: String)
