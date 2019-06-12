package model

import play.api.libs.json._

object OptionalRobustIntFormat extends Format[Option[Int]] {

  val read = new Reads[Option[Int]] {
    override def reads(json: JsValue): JsResult[Option[Int]] =
      json match {
        case JsNull => JsSuccess(None)
        case JsString(s) =>
          try {
            JsSuccess(
              if (s.isEmpty) None else Some(s.toInt)
            )
          } catch {
            case _: NumberFormatException => JsError(s"$s is not a number.")
          }
        case JsNumber(n) =>
          JsSuccess(Some(n.toInt))
        case _ => JsError(s"String or number expected but got '$json'.")
      }
  }

  val write = new Writes[Option[Int]] {
    override def writes(o: Option[Int]): JsValue = {
      o match {
        case Some(number) => JsNumber(number)
        case None => JsNull
      }
    }
  }

  override def reads(json: JsValue): JsResult[Option[Int]] =
    read.reads(json)

  override def writes(o: Option[Int]): JsValue =
    write.writes(o)
}