package org.ada.web.models

import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

case class D3Node(_id: Option[BSONObjectID], name: String, size: Option[Int] = None, var children: Seq[D3Node] = Seq())

object D3Node {
  implicit val d3NodeFormat = Json.format[D3Node]
}