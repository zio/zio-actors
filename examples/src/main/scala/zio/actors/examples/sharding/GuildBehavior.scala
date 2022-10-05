package zio.actors.examples.sharding

import com.devsisters.shardcake.{ EntityType, Sharding }
import com.devsisters.shardcake.Messenger.Replier
import zio.ZIO
import zio.actors.ActorRef
import zio.actors.persistence.EventSourcedStateful

import scala.util.Try
import zio.actors.sharding.Behavior

object GuildBehavior extends Behavior[GuildEntity.GuildMessage] {
  type State       = GuildEventSourced.GuildState
  type Message[+A] = GuildEventSourced.GuildMessage[A]
  type Event       = GuildEventSourced.GuildEvent

  val stateEmpty: State = GuildEventSourced.GuildState.empty

  def eventSourcedFactory: String => EventSourcedStateful[Any, State, Message, Event] =
    persistenceId => GuildEventSourced.handler(persistenceId)

  def messageHandler(message: GuildEntity.GuildMessage, actor: ActorRef[Message]): ZIO[Sharding, Throwable, Unit] =
    message match {
      case GuildEntity.GuildMessage.Join(userId, replier) =>
        actor
          .?(GuildEventSourced.Join(userId))
          .flatMap { tryMembers =>
            replier.reply(tryMembers)
          }
      case GuildEntity.GuildMessage.Leave(userId)         =>
        actor
          .?(GuildEventSourced.Leave(userId))
          .unit
    }
}

object GuildEntity {
  sealed trait GuildMessage

  object GuildMessage {
    case class Join(userId: String, replier: Replier[Try[Set[String]]]) extends GuildMessage
    case class Leave(userId: String)                                    extends GuildMessage
  }

  object GuildEntityType extends EntityType[GuildMessage]("GuildEntity")
}
