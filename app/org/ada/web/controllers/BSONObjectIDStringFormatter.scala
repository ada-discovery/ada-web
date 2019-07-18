package org.ada.web.controllers

import play.api.data.FormError
import play.api.data.format.Formatter
import reactivemongo.bson.BSONObjectID

object BSONObjectIDStringFormatter extends Formatter[BSONObjectID] {

  override def bind(key: String, data: Map[String, String]) = {
    try {
      data.get(key).map(value =>
        BSONObjectID.parse(value).toOption.map(
          Right(_)
        ).getOrElse(
          Left(List(FormError(key, s"String $value for the key '$key' cannot be parsed to BSONObjectID.")))
        )
      ).getOrElse(
        Left(List(FormError(key, s"No value found for the key '$key'")))
      )
    } catch {
      case e: Exception => Left(List(FormError(key, e.getMessage)))
    }
  }

  def unbind(key: String, value: BSONObjectID) =
    Map(key -> value.stringify)
}
