package rms

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import rms.Business.Employees

class AsyncTestingExampleSpec
  extends AnyWordSpec
    with BeforeAndAfterAll
    with Matchers {
  val testKit = ActorTestKit()


  override def afterAll(): Unit = testKit.shutdownTestKit()


  "Something" must {
    "behave correctly" in {
      val business = testKit.spawn(Business("uptech"), "uptech")
      val probe = testKit.createTestProbe[Business.Noted]()
      business ! Business.Joined("Aman", probe.ref)
      probe.expectMessage(Business.Noted("uptech"))

      val employeeProbe = testKit.createTestProbe[Business.Employees]()

      business ! Business.QueryEmployees(employeeProbe.ref)
      employeeProbe.expectMessage(Employees(Set("Aman")))
    }
  }
}