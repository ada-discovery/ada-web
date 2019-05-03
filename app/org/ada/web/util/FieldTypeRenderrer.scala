package org.ada.web.util

import org.ada.server.models.{Field, FieldTypeId}
import play.api.libs.json.JsValue
import play.twirl.api.{Html, HtmlFormat}
import org.ada.server.field.FieldTypeHelper
import reactivemongo.bson.BSONObjectID
import views.html.dataset.{renderers => rendererView}
import scala.collection.immutable.{ Seq => ISeq }

trait FieldTypeRenderer {
  def apply(json: Option[JsValue]): Html
}

private class FieldTypeRendererImpl(field: Field) extends FieldTypeRenderer {
  private val ftf = FieldTypeHelper.fieldTypeFactory()
  private val fieldType = ftf(field.fieldTypeSpec)

  override def apply(json: Option[JsValue]): Html = {
    val displayString = json.map(fieldType.jsonToDisplayString).getOrElse("")
    Html(displayString)
  }
}

object FieldTypeRenderer {
  def apply(field: Field): FieldTypeRenderer = new FieldTypeRendererImpl(field)
}

object FieldTypeFullRenderer {
  type FieldTypeFullInput = (BSONObjectID, String, Option[JsValue])
  type FieldTypeFullRenderer = FieldTypeFullInput => Html

  private def jsonRender(fieldLabel: String): FieldTypeFullRenderer = {
    input: FieldTypeFullInput =>
      rendererView.jsonFieldLink(input._1, input._2, fieldLabel, false)
  }

  private def arrayRender(fieldLabel: String): FieldTypeFullRenderer = {
    input: FieldTypeFullInput =>
      HtmlFormat.fill(ISeq(
        rendererView.jsonFieldLink(input._1, input._2, fieldLabel, true),
        rendererView.arrayFieldLink(input._1, input._2, fieldLabel)
      ))
  }

  private def jsonArrayRender(fieldLabel: String): FieldTypeFullRenderer = {
    input: FieldTypeFullInput =>
      HtmlFormat.fill(ISeq(
        rendererView.jsonFieldLink(input._1, input._2, fieldLabel, true),
        rendererView.arrayFieldLink(input._1, input._2, fieldLabel)
      ))
  }

  def apply(field: Field): FieldTypeFullRenderer =
    if (field.isArray) {
      if(field.fieldType == FieldTypeId.Json)
        jsonArrayRender(field.labelOrElseName)
      else
        arrayRender(field.labelOrElseName)
    } else {
      if(field.fieldType == FieldTypeId.Json)
        jsonRender(field.labelOrElseName)
      else {
        val renderer = FieldTypeRenderer(field)
        input => renderer(input._3)
      }
    }
}