package zio.actors.examples.persistence

import zio.actors.{ Supervisor, _ }
import zio._
import zio.actors.examples.persistence.Deps._
import zio.actors.examples.persistence.ShoppingCart._
import zio.test.Assertion._
import zio.test.{ Summary => _, assert, testClock, TestFailure, ZIOSpecDefault }

import java.io.File
import java.util.concurrent.TimeUnit

object Deps {

  val actorSystem: ZIO[Any, TestFailure.Runtime[Nothing], ActorSystem] = ZIO.scoped {
    ActorSystem("testSys", Some(new File((ClassLoader.getSystemClassLoader.getResource("application.conf").getFile))))
      .mapError(e => TestFailure.Runtime(Cause.die(e)))
      .withFinalizer(a => a.shutdown.catchAll(_ => ZIO.unit))
  }

  val actorSystemLayer = ZLayer.fromZIO(actorSystem)

  val recoverPolicy: Supervisor[Any] = Supervisor.retry(Schedule.exponential(Duration(200, TimeUnit.MILLISECONDS)))

}

object ShoppingCartSpec extends ZIOSpecDefault {

  def spec =
    suite("The Shopping Cart should")(
      test("add item") {
        for {
          as   <- ZIO.environment[ActorSystem].map(_.get)
          cart <- as.make("cart1", recoverPolicy, State.empty, ShoppingCart("cart1"))
          conf <- cart ? AddItem("foo", 42)
        } yield assert(conf)(equalTo(Accepted(Summary(Map("foo" -> 42), checkedOut = false))))

      },
      test("reject already added item") {
        for {
          as    <- ZIO.environment[ActorSystem].map(_.get)
          cart  <- as.make("cart2", recoverPolicy, State.empty, ShoppingCart("cart2"))
          conf1 <- cart ? AddItem("foo", 42)
          conf2 <- cart ? AddItem("foo", 13)
        } yield assert(conf1)(isSubtype[Accepted](anything)) && assert(conf2)(isSubtype[Rejected](anything))
      },
      test("remove item") {

        for {
          as    <- ZIO.environment[ActorSystem].map(_.get)
          cart  <- as.make("cart3", recoverPolicy, State.empty, ShoppingCart("cart3"))
          conf1 <- cart ? AddItem("foo", 42)
          conf2 <- cart ? RemoveItem("foo")
        } yield assert(conf1)(isSubtype[Accepted](anything)) && assert(conf2)(
          equalTo(Accepted(Summary(Map.empty, checkedOut = false)))
        )

      },
      test("adjust quantity") {
        for {
          as    <- ZIO.environment[ActorSystem].map(_.get)
          cart  <- as.make("cart4", recoverPolicy, State.empty, ShoppingCart("cart4"))
          conf1 <- cart ? AddItem("foo", 42)
          conf2 <- cart ? AdjustItemQuantity("foo", 43)
        } yield assert(conf1)(isSubtype[Accepted](anything)) && assert(conf2)(
          equalTo(Accepted(Summary(Map("foo" -> 43), checkedOut = false)))
        )

      },
      test("checkout") {

        for {
          as    <- ZIO.environment[ActorSystem].map(_.get)
          cart  <- as.make("cart5", recoverPolicy, State.empty, ShoppingCart("cart5"))
          conf1 <- cart ? AddItem("foo", 42)
          conf2 <- cart ? Checkout
        } yield assert(conf1)(isSubtype[Accepted](anything)) && assert(conf2)(
          equalTo(Accepted(Summary(Map("foo" -> 42), checkedOut = true)))
        )

      },
      test("keep its state") {

        for {
          as     <- ZIO.environment[ActorSystem].map(_.get)
          cart   <- as.make("cart6", recoverPolicy, State.empty, ShoppingCart("cart6"))
          conf1  <- cart ? AddItem("foo", 42)
          _      <- cart.stop
          cart   <- as.make("cart6", recoverPolicy, State.empty, ShoppingCart("cart6"))
          status <- cart ? Get
        } yield assert(conf1)(equalTo(Accepted(Summary(Map("foo" -> 42), checkedOut = false)))) &&
          assert(status)(equalTo(Summary(Map("foo" -> 42), checkedOut = false)))
      }
    ).provideCustomLayer(actorSystemLayer ++ ZLayer.fromZIO(testClock))
}
