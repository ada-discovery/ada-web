package org.ada.web.models

import org.ada.server.models.{Category, Field}
import play.api.libs.json.{JsObject, Json}

case class JsTreeNode(
  id: String,
  parent: String,
  text: String,
  `type`: Option[String] = None,
  data: Option[JsObject] = None
  // 'state' : { 'opened' : true, 'selected' : true },
)

object JsTreeNode {
  implicit val format = Json.format[JsTreeNode]

  def fromCategory(category: Category) =
    JsTreeNode(
      category._id.map(_.stringify).getOrElse(""),
      category.parentId.map(_.stringify).getOrElse("#"),
      category.labelOrElseName,
      Some("category"),
      Some(Json.obj("label" -> category.label))
    )

  def fromField(field: Field, nonNullCount: Option[Int]) = {
    val countText = nonNullCount.map(" (" + _ + ")").getOrElse("")
    val countJson = nonNullCount.map( count => Json.obj("nonNullCount" -> count)).getOrElse(Json.obj())

    JsTreeNode(
      field.name,
      field.categoryId.map(_.stringify).getOrElse("#"),
      field.labelOrElseName + countText,
      Some("field-" + field.fieldType.toString),
      Some(Json.obj("label" -> field.label) ++  countJson)
    )
  }
}