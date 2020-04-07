package com.rp.rms

import java.util.UUID

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.rp.rms.writer.Business
import org.scalatest.wordspec.AnyWordSpecLike
import com.rp.rms.writer.Business.BusinessSummary
import commons.{Accepted, Confirmation, _}

class BusinessSpec extends ScalaTestWithActorTestKit(s"""
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID.randomUUID().toString}"
    """) with AnyWordSpecLike {

  "Business should " should {

    "employ person" in {
      val business = testKit.spawn(Business("IBM", Set.empty))
      val probe = testKit.createTestProbe[Confirmation]

      business ! Business.Join("Alice", probe.ref)

      probe.expectMessage(Accepted(BusinessSummary(Set("Alice"))))
    }

    "let people leave" in {
      val business = testKit.spawn(Business("Microsoft", Set.empty))
      val probe = testKit.createTestProbe[Confirmation]

      business ! Business.Join("Bob", probe.ref)
      probe.expectMessage(Accepted(BusinessSummary(Set("Bob"))))
      business ! Business.Leave("Bob", probe.ref)

      probe.expectMessage(Accepted(BusinessSummary(Set())))
    }

    "keep its state" in {
      val business = testKit.spawn(Business("Google", Set.empty))
      val probe = testKit.createTestProbe[Confirmation]
      business ! Business.Join("Mike", probe.ref)
      probe.expectMessage(Accepted(BusinessSummary(Set("Mike"))))

      testKit.stop(business)

      val restartedBusiness = testKit.spawn(Business("Google", Set.empty))
      val stateProbe = testKit.createTestProbe[Confirmation]
      restartedBusiness ! Business.Join("Mike", stateProbe.ref)

      stateProbe.expectMessage(Rejected("Mike already works for Google"))
    }
  }

}
