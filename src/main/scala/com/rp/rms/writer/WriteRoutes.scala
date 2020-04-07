package com.rp.rms.writer

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.http.scaladsl.model.StatusCodes.{BadRequest, OK}
import akka.http.scaladsl.server.Directive._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.rp.rms.commons.{Accepted, Confirmation, Rejected}

import scala.concurrent.Future


class WriteRoutes()(implicit system: ActorSystem[_]) {

  implicit private val timeout: Timeout =
    Timeout.create(system.settings.config.getDuration("rms.askTimeout"))
  private val sharding = ClusterSharding(system)

  import akka.http.scaladsl.server.Directives._

  val writeRoutes: Route =
    concat(
      pathPrefix("connections") {
        concat(
          path(Segment / "friends" / Segment) { (one, another) =>
            post {
              val entityRef = sharding.entityRefFor(Connection.EntityKey, Connection.makePersistenceKey(one, another))
              completeFuture(entityRef.ask(ref => Connection.BecomeFriends(ref)))
            }
          },
          path(Segment / "friends" / Segment) { (one, another) =>
            delete {
              val entityRef = sharding.entityRefFor(Connection.EntityKey, Connection.makePersistenceKey(one, another))
              completeFuture(entityRef.ask(ref => Connection.UnFriend(ref)))
            }
          },
          path(Segment / "relatives" / Segment) { (one, another) =>
            post {
              val entityRef = sharding.entityRefFor(Connection.EntityKey, Connection.makePersistenceKey(one, another))
              completeFuture(entityRef.ask(ref => Connection.BecomeRelatives(ref)))
            }
          },
        )
      },
      path("businesses" / Segment / "employees" / Segment) { (business, employee) =>
        post {
          val entityRef = sharding.entityRefFor(Business.EntityKey, business)
          completeFuture(entityRef.ask(ref => Business.Join(employee, ref)))
        }
      }
    )

  private def completeFuture(reply: Future[Confirmation]) = {
    onSuccess(reply) {
      case Accepted(_) =>
        complete(OK -> "")
      case Rejected(reason) =>
        complete(BadRequest -> reason)
    }
  }
}