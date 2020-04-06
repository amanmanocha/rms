package rms

object JsonFormats {

  import spray.json.RootJsonFormat
  import spray.json.DefaultJsonProtocol._


  implicit val bNotedFormat: RootJsonFormat[Business.Noted] = jsonFormat1(Business.Noted)
  implicit val pNotedFormat: RootJsonFormat[Person.Noted] = jsonFormat1(Person.Noted)

  implicit val queryFriendsFormat: RootJsonFormat[Person.Friends] = jsonFormat1(Person.Friends)
  implicit val queryRelativesFormat: RootJsonFormat[Person.Relatives] = jsonFormat1(Person.Relatives)
  implicit val employeesFormat: RootJsonFormat[Business.Employees] = jsonFormat1(Business.Employees)

}
