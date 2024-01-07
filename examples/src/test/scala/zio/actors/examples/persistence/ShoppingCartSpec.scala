package zio.actors.examples.persistence

import zio.actors.Supervisor
import zio.actors.examples.persistence.Deps.*
import zio.actors.examples.persistence.ShoppingCart.*
import zio.test.Assertion.*
import zio.test.{ assert, assertTrue, Spec, ZIOSpecDefault }
import zio.{ Duration, Schedule, Scope, ZIO, ZLayer }

import java.io.File
import java.util.concurrent.TimeUnit
import zio.actors.ActorSystem

object Deps {
  val actorSystem: ZIO[Scope, Throwable, ActorSystem] =
    ZIO.acquireRelease(
      ActorSystem("testSys", Some(new File("./src/test/resources/application.conf")))
    )(_.shutdown.ignore)

  val actorSystemLayer = ZLayer.scoped(actorSystem)

  val recoverPolicy = Supervisor.retry(Schedule.exponential(Duration(200, TimeUnit.MILLISECONDS)))

}

object ShoppingCartSpec extends ZIOSpecDefault {
  def spec: Spec[Any, Throwable] =
    suite("The Shopping Cart should")(
      test("add item") {
        for {
          as   <- ZIO.service[ActorSystem]
          cart <- as.make("cart1", recoverPolicy, State.empty, ShoppingCart("cart1"))
          conf <- cart ? AddItem("foo", 42)
        } yield assertTrue(conf == Accepted(Summary(Map("foo" -> 42), checkedOut = false)))

      },
      test("reject already added item") {
        for {
          as    <- ZIO.service[ActorSystem]
          cart  <- as.make("cart2", recoverPolicy, State.empty, ShoppingCart("cart2"))
          conf1 <- cart ? AddItem("foo", 42)
          conf2 <- cart ? AddItem("foo", 13)
        } yield assert(conf1)(isSubtype[Accepted](anything)) && assert(conf2)(isSubtype[Rejected](anything))
      },
      test("remove item") {

        for {
          as    <- ZIO.service[ActorSystem]
          cart  <- as.make("cart3", recoverPolicy, State.empty, ShoppingCart("cart3"))
          conf1 <- cart ? AddItem("foo", 42)
          conf2 <- cart ? RemoveItem("foo")
        } yield assert(conf1)(isSubtype[Accepted](anything)) && assertTrue(
          conf2 == Accepted(Summary(Map.empty, checkedOut = false))
        )

      },
      test("adjust quantity") {
        for {
          as    <- ZIO.service[ActorSystem]
          cart  <- as.make("cart4", recoverPolicy, State.empty, ShoppingCart("cart4"))
          conf1 <- cart ? AddItem("foo", 42)
          conf2 <- cart ? AdjustItemQuantity("foo", 43)
        } yield assert(conf1)(isSubtype[Accepted](anything)) && assertTrue(
          conf2 == Accepted(Summary(Map("foo" -> 43), checkedOut = false))
        )

      },
      test("checkout") {

        for {
          as    <- ZIO.service[ActorSystem]
          cart  <- as.make("cart5", recoverPolicy, State.empty, ShoppingCart("cart5"))
          conf1 <- cart ? AddItem("foo", 42)
          conf2 <- cart ? Checkout
        } yield assert(conf1)(isSubtype[Accepted](anything)) && assertTrue(
          conf2 == Accepted(Summary(Map("foo" -> 42), checkedOut = true))
        )

      },
      test("keep its state") {

        for {
          as     <- ZIO.service[ActorSystem]
          cart   <- as.make("cart6", recoverPolicy, State.empty, ShoppingCart("cart6"))
          conf1  <- cart ? AddItem("foo", 42)
          _      <- cart.stop
          cart   <- as.make("cart6", recoverPolicy, State.empty, ShoppingCart("cart6"))
          status <- cart ? Get
        } yield assertTrue(
          conf1 == Accepted(Summary(Map("foo" -> 42), checkedOut = false)),
          status == Summary(Map("foo" -> 42), checkedOut = false)
        )
      }
    ).provide(actorSystemLayer)
}
