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

class EnumStringBindable[E <: Enumeration](enum: E) extends QueryStringBindable[E#Value] {

  private val stringBinder = implicitly[QueryStringBindable[String]]

  override def bind(
    key: String,
    params: Map[String, Seq[String]]
  ): Option[Either[String, E#Value]] = {
    for {
      leftRightString <- stringBinder.bind(key, params)
    } yield {
      leftRightString match {
        case Right(string) =>
          try {
            Right(enum.withName(string))
          } catch {
            case e: NoSuchElementException => Left(s"Unable to bind enum from String $string for the key $key")
          }
        case Left(msg) => Left(msg)
      }
    }
  }

  override def unbind(key: String, value: E#Value): String =
    stringBinder.unbind(key, value.toString)
}

// TODO: Move to core
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