package com.rp.rms

import java.util.UUID

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.rp.rms.writer.Connection.{Friend, Relative}
import org.scalatest.wordspec.AnyWordSpecLike
import com.rp.rms.writer.Connection.{ConnectionSummary, Friend, Relative}
import com.rp.rms.commons.{Accepted, Confirmation, Rejected}
import com.rp.rms.writer.Connection

class ConnectionSpec extends ScalaTestWithActorTestKit(s"""
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID.randomUUID().toString}"
    """) with AnyWordSpecLike {

  "Connection should " should {

    "connect friends" in {
      val connection = testKit.spawn(Connection.between("Mike", "Bob", Set.empty))
      val probe = testKit.createTestProbe[Confirmation]

      connection ! Connection.BecomeFriends(probe.ref)

      probe.expectMessage(Accepted(ConnectionSummary(Set(Friend))))
    }

    "connect relatives" in {
      val connection = testKit.spawn(Connection.between("Jim", "Pam", Set.empty))
      val probe = testKit.createTestProbe[Confirmation]

      connection ! Connection.BecomeRelatives(probe.ref)

      probe.expectMessage(Accepted(ConnectionSummary(Set(Relative))))
    }

    "unfriend people" in {
      val connection = testKit.spawn(Connection.between("Andy", "Angela", Set.empty))
      val probe = testKit.createTestProbe[Confirmation]

      connection ! Connection.BecomeFriends(probe.ref)

      probe.expectMessage(Accepted(ConnectionSummary(Set(Friend))))

      connection ! Connection.UnFriend(probe.ref)
      probe.expectMessage(Accepted(ConnectionSummary(Set())))
    }

    "keep its state" in {
      val connection = testKit.spawn(Connection.between("Mike", "Pam", Set.empty))
      val probe = testKit.createTestProbe[Confirmation]

      connection ! Connection.BecomeFriends(probe.ref)

      probe.expectMessage(Accepted(ConnectionSummary(Set(Friend))))

      testKit.stop(connection)

      val restartedConnection = testKit.spawn(Connection.between("Mike", "Pam", Set.empty))
      val stateProbe = testKit.createTestProbe[Confirmation]
      restartedConnection ! Connection.BecomeFriends(stateProbe.ref)

      stateProbe.expectMessage(Rejected("Mike and Pam are already friends"))
    }
  }

}
