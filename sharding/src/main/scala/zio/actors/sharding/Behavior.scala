package zio.actors.sharding

import com.devsisters.shardcake.Messenger.Replier
import com.devsisters.shardcake.Sharding
import zio.actors.Actor.AbstractStateful
import zio.actors.ActorRef
import zio.actors.sharding.utils.ActorFinder
import zio.actors.sharding.utils.Layers.ActorSystemZ
import zio.{ Dequeue, RIO, ZIO }

trait Behavior {

  type State

  type Command[+_]

  type Dependencies

  type Actor = AbstractStateful[Any, State, Command]

  def stateEmpty: State

  def actorFactory: Dependencies => String => Actor

}

object Behavior {

  sealed trait Message[A, Command[_]] {
    def command: Command[A]
  }
  case class AskMessage[A, Command[_]](
    command: Command[A],
    replier: Replier[A]
  ) extends Message[A, Command]

  case class TellMessage[A, Command[_]](
    command: Command[A]
  ) extends Message[A, Command]

  def create(b: Behavior)(requirements: => b.Dependencies)(
    entityId: String,
    messages: Dequeue[Behavior.Message[_, b.Command]]
  ): RIO[Sharding with ActorSystemZ, Nothing] =
    ZIO.logInfo(s"Started entity $entityId") *>
      messages.take.flatMap(handleMessage(b)(requirements)(entityId, _)).forever

  private def handleMessage(b: Behavior)(requirements: => b.Dependencies)(
    entityId: String,
    message: Behavior.Message[_, b.Command]
  ): RIO[Sharding with ActorSystemZ, Unit] =
    ActorFinder
      .ref[b.State, b.Command](
        entityId,
        b.stateEmpty,
        b.actorFactory(requirements)
      )
      .flatMap(messageHandler(b)(message, _))

  private def messageHandler(b: Behavior)(
    message: Behavior.Message[_, b.Command],
    actor: ActorRef[b.Command]
  ): ZIO[Sharding, Throwable, Unit] =
    message match {
      case AskMessage(command, replier) =>
        actor
          .?(command)
          .flatMap(replier.reply)
      case TellMessage(command)         =>
        actor
          .!(command)
    }

}
