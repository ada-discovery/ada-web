package org.ada.web.controllers.dataset.datatrans

import org.ada.server.models.datatrans.LinkTwoDataSetsTransformation
import org.ada.server.json.TupleFormat
import org.incal.play.formatters.JsonFormatter
import org.incal.play.controllers.WebContext
import play.api.data.Forms.{of, seq}
import views.html.{datasettrans => view}

object LinkTwoDataSetsFormViews extends DataSetTransformationFormViews[LinkTwoDataSetsTransformation] {

  private implicit val tupleFormat = TupleFormat[String, String]
  private implicit val tupleFormatter = JsonFormatter[(String, String)]

  override protected val extraMappings =
    Seq(
      "linkFieldNames" -> seq(of[(String, String)]).verifying(
        "Link must contain at least one (left,right)-pair of fields.",
          linkFieldNames => linkFieldNames.nonEmpty
        )
    )

  override protected def viewElements(implicit webContext: WebContext) =
    idForm => view.linkTwoDataSetsElements(idForm.id, idForm.form)
}