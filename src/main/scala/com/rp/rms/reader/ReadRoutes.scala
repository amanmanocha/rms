package com.rp.rms.reader

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.rp.rms.reader.ReadStore.{Businesses, Persons}

class ReadRoutes(proxy: ActorRef[ReadStore.Command])(implicit system: ActorSystem[_]) {

  implicit private val timeout: Timeout =
    Timeout.create(system.settings.config.getDuration("rms.askTimeout"))

  import JsonFormats._
  import akka.actor.typed.scaladsl.AskPattern._
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import akka.http.scaladsl.server.Directives._

  import scala.concurrent.Future

  val routes: Route =

    concat(

      path("persons" / Segment / "relatives") { person =>
        get {
          val result: Future[Persons] = proxy.ask(ref => ReadStore.GetRelatives(person, ref))
          onSuccess(result) {
            case persons@Persons(_) =>
              complete(StatusCodes.OK -> persons)
          }
        }
      },
      path("businesses" / Segment / "employees" / "relatives") { business =>
        get {
          val result: Future[Persons] = proxy.ask(ref => ReadStore.GetEmployeeRelatives(business, ref))
          onSuccess(result) {
            case persons@Persons(_) =>
              complete(StatusCodes.OK -> persons)
          }
        }
      },
      path("businesses") {
        parameters("strengthGreaterThan") { strength =>
          get {
            val result: Future[Businesses] = proxy.ask(ref => ReadStore.GetBusinessesWithStrengthGreaterThan(strength.toInt, ref))
            onSuccess(result) {
              case businesses@Businesses(_) =>
                complete(StatusCodes.OK -> businesses)
            }
          }
        }
      },
      path("businesses" / "employees" / "relatives" / "friends") {
        get {
          val result: Future[Persons] = proxy.ask(ref => ReadStore.GetEmployedRelativesFriends(ref))
          onSuccess(result) {
            case persons@Persons(_) =>
              complete(StatusCodes.OK -> persons)
          }
        }
      },
    )

}


object JsonFormats {

  import spray.json.DefaultJsonProtocol._
  import spray.json.RootJsonFormat

  implicit val personsFormat: RootJsonFormat[Persons] = jsonFormat1(Persons)
  implicit val businessFormat: RootJsonFormat[Businesses] = jsonFormat1(Businesses)

}