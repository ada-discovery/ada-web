package org.ada.web.controllers.dataset.datatrans

import org.ada.server.json.TupleFormat
import org.ada.server.models.datatrans.RenameFieldsTransformation
import org.incal.play.controllers.WebContext
import org.incal.play.formatters.JsonFormatter
import play.api.data.Form
import play.api.data.Forms.{of, seq}
import views.html.{datasettrans => view}

object RenameFieldsFormViews extends DataSetTransformationFormViews[RenameFieldsTransformation] {

  private implicit val tupleFormat = TupleFormat[String, String]
  private implicit val tupleFormatter = JsonFormatter[(String, String)]

  override protected val extraMappings =
    Seq(
      "fieldOldNewNames" -> seq(of[(String, String)])
    )

  override protected def viewElements(implicit webContext: WebContext) =
    idForm => view.renameFieldsElements(idForm.id, idForm.form)
}