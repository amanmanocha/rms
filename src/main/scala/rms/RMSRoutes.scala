package rms

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import rms.Person.Relatives

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._


private[rms] final class RMSRoutes(system: ActorSystem[_]) {

  private val sharding = ClusterSharding(system)

  // timeout used for asking the actor
  private implicit val timeout: Timeout =
    system.settings.config.getDuration("rms.routes.ask-timeout").toMillis.millis

  private def join(business: String, person: String): Future[Business.Noted] = {
    val bRef = sharding.entityRefFor(Business.TypeKey, business)
    bRef.ask(Business.Joined(person, _))
  }

  private def makeFriend(person: String, friend: String): Future[Person.Noted] = {

    for {
      _ <- sharding.entityRefFor(Person.TypeKey, person).ask(Person.BecomeFriendsWith(friend, _))
      f2 <- sharding.entityRefFor(Person.TypeKey, friend).ask(Person.BecomeFriendsWith(person, _))
    } yield f2
  }

  private def makeRelative(person: String, relative: String): Future[Person.Noted] = {
    for {
      f1 <- sharding.entityRefFor(Person.TypeKey, person).ask(Person.BecomeRelativeWith(relative, _))
      f2 <- sharding.entityRefFor(Person.TypeKey, relative).ask(Person.BecomeRelativeWith(person, _))
    } yield f2
  }

  private def queryRelatives(person: String): Future[Person.Relatives] = {
    val ref = sharding.entityRefFor(Person.TypeKey, person)
    ref.ask(Person.QueryRelatives(_))
  }

  private def queryFriends(person: String): Future[Person.Friends] = {
    val ref = sharding.entityRefFor(Person.TypeKey, person)
    ref.ask(Person.QueryFriends(_))
  }

  def queryBusinessRelatives(business: String): Future[Relatives] = {
    sharding.entityRefFor(Business.TypeKey, business)
      .ask(Business.QueryEmployees)
      .map(employees => employees.employees)
      .flatMap(employees => {
        Future.sequence(employees.map(person => {
          sharding.entityRefFor(Person.TypeKey, person).ask(Person.QueryRelatives).map(relatives => relatives.relatives)
        }))
      }).map(relatives => Relatives(relatives.flatten))
  }

  import JsonFormats._
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import akka.http.scaladsl.server.Directives._


  val routes: Route =
    concat(
      path("businesses" / Segment / "persons" / Segment) { (business, person) =>
        concat(
          get {
            complete(join(business, person))
          }
        )
      },
      path("persons" / Segment / "friends" / Segment) { (person, friend) =>
        concat(
          get {
            complete(makeFriend(person, friend))
          }
        )
      },
      path("persons" / Segment / "relatives" / Segment) { (person, relative) =>
        concat(
          get {
            complete(makeRelative(person, relative))
          }
        )
      },
      path("q" / "persons" / Segment / "relatives") { person =>
        concat(
          get {
            complete(queryRelatives(person))
          }
        )
      },
      path("q" / "persons" / Segment / "friends") { person =>
        concat(
          get {
            complete(queryFriends(person))
          }
        )
      },
      path("q" / "businesses" / Segment / "relatives") { business =>
        concat(
          get {
            complete(queryBusinessRelatives(business))
          }
        )
      },
      path("q" / "businesses" / Segment / "relatives") { business =>
        concat(
          get {
            complete(queryBusinessRelatives(business))
          }
        )
      }
    )

}
