package zio.actors.sharding.utils

import com.devsisters.shardcake.Messenger
import zio.Task
import zio.actors.sharding.Behavior

object MessengerOps {

  implicit class MessengerClassOps[Cmd[_]](messenger: Messenger[Behavior.Message[_, Cmd]]) {
    def ask[A](entityId: String)(cmd: Cmd[A]): Task[A] =
      messenger.send[A](entityId)(Behavior.Message(cmd, _))
  }

}
