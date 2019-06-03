package org.ada.web.controllers.dataset.datatrans

import org.ada.server.models.datatrans.{CopyDataSetTransformation, DropFieldsTransformation}
import org.incal.play.controllers.WebContext
import play.api.data.Form
import views.html.{datasettrans => view}

object DropFieldsFormViews extends DataSetTransformationFormViews[DropFieldsTransformation] {

  override protected[controllers] val displayName =
    "Drop Fields"

  override protected val viewElements =
    view.dropFieldsElements(_: Form[DropFieldsTransformation])(_: WebContext)
}