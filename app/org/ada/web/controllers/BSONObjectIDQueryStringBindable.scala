package org.ada.web.controllers

import java.util.NoSuchElementException

import play.api.data.FormError
import play.api.data.format.Formatter
import play.api.mvc.QueryStringBindable
import reactivemongo.bson.BSONObjectID

object BSONObjectIDQueryStringBindable extends QueryStringBindable[BSONObjectID] {

  private val stringBinder = implicitly[QueryStringBindable[String]]

  override def bind(
    key: String,
    params: Map[String, Seq[String]]
  ): Option[Either[String, BSONObjectID]] = {
    for {
      leftRightString <- stringBinder.bind(key, params)
    } yield {
      leftRightString match {
        case Right(string) =>
          BSONObjectID.parse(string).toOption.map(Right(_)).getOrElse(
            Left(s"Unable to bind BSON Object Id from String $string to $key.")
          )
        case _ => Left(s"Unable to bind BSON Object Id from a non-String to $key.")
      }
    }
  }

  override def unbind(key: String, id: BSONObjectID): String =
    stringBinder.unbind(key, id.stringify)
}