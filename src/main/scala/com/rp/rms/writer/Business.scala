package com.rp.rms.writer

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria}
import com.rp.rms.commons._
import com.rp.rms.reader.EventProcessorSettings
import com.rp.rms.{CborSerializable, commons}
import scala.concurrent.duration._

object Business {

  final case class State(name: String, employees: Set[String]) extends CborSerializable {
    def joined(employee: String): State = {
      copy(employees = employees + employee)
    }

    def left(employee: String): State = {
      copy(employees = employees - employee)
    }
  }

  object State {
    def apply(name: String): State = new State(name, Set.empty)
  }

  final case class BusinessSummary(employees: Set[String]) extends Summary

  final case class Join(employee: String, replyTo: ActorRef[Confirmation]) extends Command

  final case class Leave(employee: String, replyTo: ActorRef[Confirmation]) extends Command

  sealed trait Event extends commons.Event  with CborSerializable

  final case class Joined(business: String, employee: String) extends Event

  final case class Left(business: String, employee: String) extends Event

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey[Command]("Business")

  def init(system: ActorSystem[_], eventProcessorSettings: EventProcessorSettings): Unit = {
    ClusterSharding(system).init(Entity(EntityKey) { entityContext =>
      Business(entityContext.entityId, Set(eventProcessorSettings.tagName))
    }.withRole("write-model"))
  }

  def apply(name: String, eventProcessorTags: Set[String]): Behavior[Command] = {
    init(name, eventProcessorTags)
  }

  def init(name: String, eventProcessorTags: Set[String]): Behavior[Command] = {
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, State](
        PersistenceId(EntityKey.name, name),
        State(name),
        (state, command) =>
          running(name, state, command),
        (state, event) => handleEvent(state, event))
      .withTagger(_ => eventProcessorTags)
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
  }

  private def running(name: String, state: State, command: Command): ReplyEffect[Event, State] =
    command match {
      case Join(employee, replyTo) =>
        if (state.employees.contains(employee))
          Effect.reply(replyTo)(Rejected(s"$employee already works for $name"))
        else
          Effect
            .persist(Joined(name, employee))
            .thenReply(replyTo)(updatedState => Accepted(BusinessSummary(updatedState.employees)))
      case Leave(employee, replyTo) =>
        if (!state.employees.contains(employee))
          Effect.reply(replyTo)(Rejected(s"$employee does not work at $name"))
        else
          Effect
            .persist(Left(name, employee))
            .thenReply(replyTo)(updatedState => Accepted(BusinessSummary(updatedState.employees)))
    }


  private def handleEvent(state: State, event: Event) = {
    event match {
      case Joined(_, employee) => state.joined(employee)
      case Left(_, employee) => state.left(employee)
    }
  }
}
