package rms

import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}

/**
 * Root actor bootstrapping the application
 */
object Guardian {

  sealed trait Command

  def apply(httpPort: Int): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
    Business.initSharding(context.system)
    Person.initSharding(context.system)
    val routes = new RMSRoutes(context.system)
    RMSHttpServer.start(routes.routes, httpPort, context.system)

    Behaviors.empty
  }

}

object Business {

  sealed trait Command extends CborSerializable

  case class Joined(name: String, replyTo: ActorRef[Noted]) extends Command
  case class QueryEmployees(replyTo: ActorRef[Employees]) extends Command

  final case class Noted(name: String) extends CborSerializable
  final case class Employees(employees: Set[String]) extends CborSerializable

  val TypeKey: EntityTypeKey[Business.Command] =
    EntityTypeKey[Business.Command]("Business")


  val BusinessServiceKey = ServiceKey[Command]("Business")


  def initSharding(system: ActorSystem[_]): Unit =
    ClusterSharding(system).init(Entity(TypeKey) { entityContext =>
      Business(entityContext.entityId)
    })

  def apply(name: String): Behavior[Command] = Behaviors.setup { context =>
    context.log.info("Starting Business {}.", name)
    context.system.receptionist ! Register(BusinessServiceKey, context.self)
    context.system.receptionist ! Register( ServiceKey[Command](s"business-$name"), context.self)
    running(context, name, Set.empty)
  }

  private def running(context: ActorContext[Command], businessName: String, employees: Set[String]): Behavior[Command] =
    Behaviors.receiveMessage[Command] {
      case Joined(employeeName, replyTo) =>
        val updated = employees.toVector :+ employeeName
        if (context.log.isDebugEnabled) {
          context.log.debug("{} joined, now employees {}.", employeeName, updated.size)
        }
        replyTo ! Noted(businessName)
        running(context, businessName, updated.toSet) // store
      case QueryEmployees(replyTo) =>
        replyTo ! Employees(employees)
        Behaviors.same
    }.receiveSignal {
      case (_, PostStop) =>
        context.log.info("Stopping, losing all recorded state for business {}", businessName)
        Behaviors.same
    }

}


object Person {

  sealed trait Command extends CborSerializable

  case class BecomeFriendsWith(name: String, replyTo: ActorRef[Noted]) extends Command

  case class BecomeRelativeWith(name: String, replyTo: ActorRef[Noted]) extends Command

  case class QueryFriends(replyTo: ActorRef[Friends]) extends Command

  case class QueryRelatives(replyTo: ActorRef[Relatives]) extends Command

  final case class Noted(name: String) extends CborSerializable

  final case class Relatives(relatives: Set[String]) extends CborSerializable

  final case class Friends(friends: Set[String]) extends CborSerializable

  val TypeKey: EntityTypeKey[Person.Command] =
    EntityTypeKey[Person.Command]("Person")

  val PersonServiceKey = ServiceKey[Command]("Person")

  def initSharding(system: ActorSystem[_]): Unit =
    ClusterSharding(system).init(Entity(TypeKey) { entityContext =>
      Person(entityContext.entityId)
    })


  def apply(name: String): Behavior[Command] = Behaviors.setup { context =>
    context.log.info("Born Person {}.", name)
    context.system.receptionist ! Register(PersonServiceKey, context.self)

    running(context, name, Set.empty, Set.empty)
  }

  private def running(context: ActorContext[Command], personName: String, friends: Set[String], relatives: Set[String]): Behavior[Command] =
    Behaviors.receiveMessage[Command] {
      case BecomeFriendsWith(friend, replyTo) =>
        val updated = friends.toVector :+ friend
        if (context.log.isDebugEnabled) {
          context.log.debug(s"$friend became friend, now friends $updated.")
        }
        replyTo ! Noted(personName)
        running(context, personName, updated.toSet, relatives)

      case BecomeRelativeWith(relative, replyTo) =>
        val updated = relatives.toVector :+ relative
        if (context.log.isDebugEnabled) {
          context.log.debug(s"$relative became relative, now relatives $updated.")
        }
        replyTo ! Noted(personName)
        running(context, personName, friends, updated.toSet)

      case QueryRelatives(replyTo) =>
        replyTo ! Relatives(relatives)
        Behaviors.same

      case QueryFriends(replyTo) =>
        replyTo ! Friends(friends)
        Behaviors.same
    }.receiveSignal {
      case (_, PostStop) =>
        context.log.info("Stopping, losing all recorded state for person {}", personName)
        Behaviors.same
    }

}
