package org.ada.web.controllers.dataset.datatrans

import org.ada.server.json.{EnumFormat, TupleFormat}
import org.ada.server.models.{Field, FieldTypeId}
import org.ada.server.models.datatrans.SwapFieldsDataSetTransformation
import org.incal.play.controllers.WebContext
import org.incal.play.formatters.JsonFormatter
import play.api.data.Forms._
import views.html.{datasettrans => view}

object SwapFieldsFormViews extends DataSetTransformationFormViews[SwapFieldsDataSetTransformation] {

  private implicit val fieldTypeFormat = EnumFormat(FieldTypeId)
  private implicit val tupleFormat = TupleFormat[String, String, FieldTypeId.Value]
  private implicit val tupleFormatter = JsonFormatter[(String, String, FieldTypeId.Value)]

  override protected val extraMappings =
    Seq(
      // TODO: ugly.. map directly to Field
      "newFields" -> seq(of[(String, String, FieldTypeId.Value)]).transform[Seq[Field]](
        _.map { case (name, label, fieldType) => Field(name, Some(label), fieldType)},
        _.map { field: Field => (field.name, field.label.getOrElse(""), field.fieldType) }
      )
    )

  override protected def viewElements(implicit webContext: WebContext) =
    idForm => view.swapFieldsElements(idForm.id, idForm.form)
}