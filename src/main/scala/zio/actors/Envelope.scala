package zio.actors

private[actors] case class Envelope(msg: Any, recipient: String)
