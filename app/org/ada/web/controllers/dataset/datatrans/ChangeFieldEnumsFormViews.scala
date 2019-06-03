package org.ada.web.controllers.dataset.datatrans

import org.ada.server.json.TupleFormat
import org.ada.server.models.datatrans.ChangeFieldEnumsTransformation
import org.incal.play.controllers.{IdForm, WebContext}
import org.incal.play.formatters.JsonFormatter
import play.api.data.Form
import play.api.data.Forms._
import views.html.{datasettrans => view}

object ChangeFieldEnumsFormViews extends DataSetTransformationFormViews[ChangeFieldEnumsTransformation] {

  private implicit val tupleFormat = TupleFormat[String, String, String]
  private implicit val tupleFormatter = JsonFormatter[(String, String, String)]

  override protected[controllers] val displayName =
    "Change Field Enums"

  override protected val extraMappings =
    Seq(
      "fieldNameOldNewEnums" -> seq(of[(String, String, String)])
    )

  override protected val viewElements =
    view.changeFieldEnumsElements(_ : Form[ChangeFieldEnumsTransformation])(_: WebContext)
}