package org.ada.web.controllers.core

import play.api.data.FormError
import play.api.data.format.Formatter

// TODO: move to core
final class SeqOptionFormatter[T](
  fromString: String => Option[T],
  toString: T => String = (x: T) => x.toString, // by default call toString
  delimiter: String = ","                       // use comma as a default delimiter
) extends Formatter[Seq[Option[T]]] {

  private def asOption(string: String) =
    if (string.nonEmpty) Some(string) else None

  def bind(key: String, data: Map[String, String]) =
    try {
      data.get(key).map { string =>
        println(string)
        val items = string.split(delimiter, -1).map(x =>
          asOption(x.trim).flatMap(fromString)
        ).toSeq

        println(items.mkString(", "))

        Right(items)
      }.getOrElse(
        Left(List(FormError(key, s"No value found for the key '$key'")))
      )
    } catch {
      case e: Exception => Left(List(FormError(key, e.getMessage)))
    }

  def unbind(key: String, list: Seq[Option[T]]) =
    Map(key -> list.map(x => x.map(toString).getOrElse("")).mkString(s"$delimiter "))
}

object SeqOptionFormatter {

  def apply: Formatter[Seq[Option[String]]] = new SeqOptionFormatter[String](x => Some(x))

  def asInt: Formatter[Seq[Option[Int]]] = new SeqOptionFormatter[Int](x => try {
    Some(x.toInt)
  } catch {
    case e: NumberFormatException => None
  })

  def asDouble: Formatter[Seq[Option[Double]]] = new SeqOptionFormatter[Double](x => try {
    Some(x.toDouble)
  } catch {
    case e: NumberFormatException => None
  })
}
