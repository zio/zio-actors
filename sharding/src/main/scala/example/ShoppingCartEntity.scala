package example

import com.devsisters.shardcake.Messenger.Replier
import zio.actors.sharding.Entity

object ShoppingCartEntity extends Entity {
  case class Message[A](
    command: ShoppingCart.Command[A],
    replier: Replier[A]
  )
  type Msg = Message[_]
  override def name: String = "ShoppingCartEntity"
}
