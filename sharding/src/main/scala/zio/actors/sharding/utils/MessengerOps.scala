package zio.actors.sharding.utils

import com.devsisters.shardcake.Messenger
import zio.actors.sharding.Behavior
import zio.{ Task, UIO }

object MessengerOps {

  implicit class MessengerClassOps[Cmd[_]](messenger: Messenger[Behavior.Message[_, Cmd]]) {
    def ask[A](entityId: String)(cmd: Cmd[A]): Task[A] =
      messenger.send[A](entityId)(Behavior.AskMessage(cmd, _))

    def tell[A](entityId: String)(cmd: Cmd[A]): UIO[Unit] =
      messenger.sendDiscard(entityId)(Behavior.TellMessage(cmd))
  }

}
