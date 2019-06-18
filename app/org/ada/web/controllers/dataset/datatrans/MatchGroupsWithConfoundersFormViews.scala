package org.ada.web.controllers.dataset.datatrans

import org.ada.server.json.{OptionFormat, TupleFormat}
import org.ada.server.models.datatrans.MatchGroupsWithConfoundersTransformation
import org.incal.play.controllers.WebContext
import org.incal.play.formatters.JsonFormatter
import play.api.data.Form
import play.api.data.Forms.{of, seq}
import play.api.data.Forms._
import views.html.{datasettrans => view}

object MatchGroupsWithConfoundersFormViews extends DataSetTransformationFormViews[MatchGroupsWithConfoundersTransformation] {

  private implicit val optionFormat = new OptionFormat[Int]
  private implicit val tupleFormat = TupleFormat[String, Option[Int]]
  private implicit val tupleFormatter = JsonFormatter[(String, Option[Int])]

  override protected val extraMappings =
    Seq(
      "confoundingFieldNames" -> seq(nonEmptyText),
      "targetGroupDisplayStringRatios" -> seq(of[(String, Option[Int])])
    )

  override protected def viewElements(implicit webContext: WebContext) =
    idForm => view.matchGroupsWithConfoundersElements(idForm.id, idForm.form)
}