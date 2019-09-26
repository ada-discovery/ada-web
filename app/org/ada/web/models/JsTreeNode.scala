package org.ada.web.models

import org.ada.server.models.{Category, Field, FilterShowFieldStyle}
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

  def fromField(
    field: Field,
    nonNullCount: Option[Int] = None,
    filterShowFieldStyle: Option[FilterShowFieldStyle.Value] = None
  ) = {
    val countText = nonNullCount.map(" (" + _ + ")").getOrElse("")
    val countJson = nonNullCount.map( count => Json.obj("nonNullCount" -> count)).getOrElse(Json.obj())

    val label =
      filterShowFieldStyle.getOrElse(FilterShowFieldStyle.LabelsAndNamesOnlyIfLabelUndefined) match {
        case FilterShowFieldStyle.NamesOnly => field.name
        case _ => field.labelOrElseName
      }

    JsTreeNode(
      field.name,
      field.categoryId.map(_.stringify).getOrElse("#"),
      label + countText,
      Some("field-" + field.fieldType.toString),
      Some(Json.obj("label" -> field.label) ++  countJson)
    )
  }
}