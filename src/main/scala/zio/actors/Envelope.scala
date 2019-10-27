package zio.actors

case class Envelope(msg: Any, recipient: String)
