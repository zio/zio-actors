package zio.actors.examples.persistence

import java.io.File
import java.util.concurrent.TimeUnit

import zio.{ Cause, Has, Managed, Schedule, UIO, ZIO, ZLayer }
import zio.actors.{ ActorSystem, Supervisor }
import zio.duration.Duration
import zio.test.Assertion._
import zio.test.{ assert, suite, testM, DefaultRunnableSpec, TestFailure }
import ShoppingCart._
import Deps._

object Deps {
  val actorSystem: Managed[TestFailure[Throwable], ActorSystem] =
    Managed.make(
      ActorSystem("testSys", Some(new File("./src/test/resources/application.conf")))
        .mapError(e => TestFailure.Runtime(Cause.die(e)))
    )(_.shutdown.catchAll(_ => UIO.unit))

  val actorSystemLayer = ZLayer.fromManaged(actorSystem)

  val recoverPolicy = Supervisor.retry(Schedule.exponential(Duration(200, TimeUnit.MILLISECONDS)))

}

object ShoppingCartSpec extends DefaultRunnableSpec {
  def spec =
    suite("The Shopping Cart should")(
      testM("add item") {
        for {
          as   <- ZIO.environment[Has[ActorSystem]].map(_.get)
          cart <- as.make("cart1", recoverPolicy, State.empty, ShoppingCart("cart1"))
          conf <- cart ? AddItem("foo", 42)
        } yield assert(conf)(equalTo(Accepted(Summary(Map("foo" -> 42), checkedOut = false))))

      },
      testM("reject already added item") {
        for {
          as    <- ZIO.environment[Has[ActorSystem]].map(_.get)
          cart  <- as.make("cart2", recoverPolicy, State.empty, ShoppingCart("cart2"))
          conf1 <- cart ? AddItem("foo", 42)
          conf2 <- cart ? AddItem("foo", 13)
        } yield assert(conf1)(isSubtype[Accepted](anything)) && assert(conf2)(isSubtype[Rejected](anything))
      },
      testM("remove item") {

        for {
          as    <- ZIO.environment[Has[ActorSystem]].map(_.get)
          cart  <- as.make("cart3", recoverPolicy, State.empty, ShoppingCart("cart3"))
          conf1 <- cart ? AddItem("foo", 42)
          conf2 <- cart ? RemoveItem("foo")
        } yield assert(conf1)(isSubtype[Accepted](anything)) && assert(conf2)(
          equalTo(Accepted(Summary(Map.empty, checkedOut = false)))
        )

      },
      testM("adjust quantity") {
        for {
          as    <- ZIO.environment[Has[ActorSystem]].map(_.get)
          cart  <- as.make("cart4", recoverPolicy, State.empty, ShoppingCart("cart4"))
          conf1 <- cart ? AddItem("foo", 42)
          conf2 <- cart ? AdjustItemQuantity("foo", 43)
        } yield assert(conf1)(isSubtype[Accepted](anything)) && assert(conf2)(
          equalTo(Accepted(Summary(Map("foo" -> 43), checkedOut = false)))
        )

      },
      testM("checkout") {

        for {
          as    <- ZIO.environment[Has[ActorSystem]].map(_.get)
          cart  <- as.make("cart5", recoverPolicy, State.empty, ShoppingCart("cart5"))
          conf1 <- cart ? AddItem("foo", 42)
          conf2 <- cart ? Checkout
        } yield assert(conf1)(isSubtype[Accepted](anything)) && assert(conf2)(
          equalTo(Accepted(Summary(Map("foo" -> 42), checkedOut = true)))
        )

      },
      testM("keep its state") {

        for {
          as     <- ZIO.environment[Has[ActorSystem]].map(_.get)
          cart   <- as.make("cart6", recoverPolicy, State.empty, ShoppingCart("cart6"))
          conf1  <- cart ? AddItem("foo", 42)
          _      <- cart.stop
          cart   <- as.make("cart6", recoverPolicy, State.empty, ShoppingCart("cart6"))
          status <- cart ? Get
        } yield assert(conf1)(equalTo(Accepted(Summary(Map("foo" -> 42), checkedOut = false)))) &&
          assert(status)(equalTo(Summary(Map("foo" -> 42), checkedOut = false)))
      }
    ).provideCustomLayer(actorSystemLayer)
}
