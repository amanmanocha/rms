package com.rp.rms.reader
import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.{Offset, PersistenceQuery}
import akka.stream.KillSwitches
import akka.stream.scaladsl.{RestartSource, Sink}
import com.rp.rms.{CborSerializable, commons}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration._

object ReadStore {
  private var rms = State.empty()

  trait Command extends CborSerializable
  case object Start extends Command
  case class Persons(persons:Set[String]) extends Command
  case class Businesses(businesses:Set[String]) extends Command

  final case class GetRelatives(person:String, replyTo: ActorRef[Persons]) extends Command
  final case class GetEmployeeRelatives(business:String, replyTo: ActorRef[Persons]) extends Command
  final case class GetEmployedRelativesFriends(replyTo: ActorRef[Persons]) extends Command
  final case class GetBusinessesWithStrengthGreaterThan(strength:Int, replyTo: ActorRef[Businesses]) extends Command
  protected val log: Logger = LoggerFactory.getLogger(getClass)

  def apply(implicit system: ActorSystem[_]): Behavior[Command] = {
    val query =
      PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

    Behaviors.receive { (_, message) =>
      message match {
        case Start => {
          val killSwitch = KillSwitches.shared("eventProcessorSwitch")
          RestartSource
            .withBackoff(minBackoff = 500.millis, maxBackoff = 20.seconds, randomFactor = 0.1) { () =>
              query.eventsByTag("RMS", Offset.noOffset).map(e => {
                rms = rms.updated(e.event.asInstanceOf[commons.Event])
                log.debug(s"Processed ${e.event}")
              })
            }
            .via(killSwitch.flow)
            .runWith(Sink.ignore)
          Behaviors.same
        }
        case GetRelatives(person, client) =>
          client ! Persons(rms.relatives.get(person).toSet)
          Behaviors.same
        case GetEmployeeRelatives(business, client) =>
          client ! Persons(rms.businessEmployeeRelatives(business))
          Behaviors.same
        case GetBusinessesWithStrengthGreaterThan(stength, client) =>
          client ! Businesses(rms.businessWithStrengthGreaterThan(stength))
          Behaviors.same
        case GetEmployedRelativesFriends(client) =>
          client ! Persons(rms.emplyedRelativeFriends())
          Behaviors.same
      }
    }
  }

}