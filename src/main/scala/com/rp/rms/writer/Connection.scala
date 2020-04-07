package com.rp.rms.writer

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria}
import com.rp.rms.commons._
import com.rp.rms.reader.EventProcessorSettings
import com.rp.rms.{CborSerializable, commons}
import scala.concurrent.duration._

object Connection {

  sealed trait RelationType
  case object Friend extends RelationType
  case object Relative extends RelationType

  final case class State(one: String, another: String, relations: Set[RelationType]) extends CborSerializable {
    def becameFriends(): State = {
      copy(relations = relations + Friend)
    }

    def unfriended(): State = {
      copy(relations = relations - Friend)
    }

    def becameRelatives(): State = {
      copy(relations = relations + Relative)
    }
  }

  object State {
    def apply(one: String, another: String): State = new State(one, another, Set.empty)
  }

  sealed trait Event extends commons.Event with CborSerializable

  final case class ConnectionSummary(relations: Set[RelationType]) extends Summary

  final case class BecomeFriends(replyTo: ActorRef[Confirmation]) extends Command
  final case class BecomeRelatives(replyTo: ActorRef[Confirmation]) extends Command
  final case class UnFriend(replyTo: ActorRef[Confirmation]) extends Command

  final case class BecameFriends(one:String, another:String) extends Event
  final case class BecameRelatives(one:String, another:String) extends Event
  final case class UnFriended(one:String, another:String) extends Event

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey[Command]("Relations")

  def init(system: ActorSystem[_], eventProcessorSettings: EventProcessorSettings): Unit = {
    ClusterSharding(system).init(Entity(EntityKey) { entityContext =>
      Connection(entityContext.entityId, Set(eventProcessorSettings.tagName))
    }.withRole("write-model"))
  }

  def apply(relationId: String, eventProcessorTags: Set[String]): Behavior[Command] = {
    between(relationId.split("_")(0), relationId.split("_")(1), eventProcessorTags)
  }

  def between(one: String, another: String, eventProcessorTags: Set[String]): Behavior[Command] = {
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, State](
        PersistenceId(EntityKey.name, makePersistenceKey(one, another)),
        State(one, another),
        (state, command) =>
          established(state, command),
        (state, event) => handleEvent(state, event))
      .withTagger(_ => eventProcessorTags)
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
  }

  def makePersistenceKey(one: String, another: String) = {
    if (one > another) one + "_" + another else another + "_" + one
  }

  private def established(state: State, command: Command): ReplyEffect[Event, State] =
    command match {
      case BecomeFriends(replyTo) =>
        if (state.relations.contains(Friend))
          Effect.reply(replyTo)(Rejected(s"${state.one} and ${state.another} are already friends"))
        else
          Effect
            .persist(BecameFriends(state.one, state.another))
            .thenReply(replyTo)(updatedState => Accepted(ConnectionSummary(updatedState.relations)))
      case UnFriend(replyTo) =>
        if (!state.relations.contains(Friend))
          Effect.reply(replyTo)(Rejected(s"${state.one} and ${state.another} are not friends"))
        else
          Effect
            .persist(UnFriended(state.one, state.another))
            .thenReply(replyTo)(updatedState => Accepted(ConnectionSummary(updatedState.relations)))
      case BecomeRelatives(replyTo) =>
        if (state.relations.contains(Relative))
          Effect.reply(replyTo)(Rejected(s"${state.one} and ${state.another} are not relatives"))
        else
          Effect
            .persist(BecameRelatives(state.one, state.another))
            .thenReply(replyTo)(updatedState => Accepted(ConnectionSummary(updatedState.relations)))
    }


  private def handleEvent(state: State, event: Event) = {
    event match {
      case BecameFriends(_, _) => state.becameFriends()
      case BecameRelatives(_, _) => state.becameRelatives()
      case UnFriended(_, _) => state.unfriended()
    }
  }
}
