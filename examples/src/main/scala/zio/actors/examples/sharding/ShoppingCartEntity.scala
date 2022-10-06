package zio.actors.examples.sharding

import com.devsisters.shardcake.EntityType
import com.devsisters.shardcake.Messenger.Replier
import zio.actors.examples.persistence.ShoppingCart

object ShoppingCartEntity {
  sealed trait Message

  final case class AddItem(
    itemId: String,
    quantity: Int,
    replier: Replier[ShoppingCart.Confirmation]
  ) extends Message
  final case class RemoveItem(
    itemId: String,
    replier: Replier[ShoppingCart.Confirmation]
  ) extends Message
  final case class AdjustItemQuantity(
    itemId: String,
    quantity: Int,
    replier: Replier[ShoppingCart.Confirmation]
  ) extends Message
  final case class Checkout(
    replier: Replier[ShoppingCart.Confirmation]
  ) extends Message
  final case class Get(
    replier: Replier[ShoppingCart.Summary]
  ) extends Message

  object ShoppingCartEntityType extends EntityType[Message]("ShoppingCartEntity")
}
