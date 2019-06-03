package org.ada.web.controllers.dataset.datatrans

import org.ada.server.models.datatrans.CopyDataSetTransformation
import org.incal.play.controllers.WebContext
import play.api.data.Form
import views.html.{datasettrans => view}

object CopyFormViews extends DataSetTransformationFormViews[CopyDataSetTransformation] {

  override protected[controllers] val displayName =
    "Copy Data Set"

  override protected val viewElements =
    view.copyDataSetElements(_: Form[CopyDataSetTransformation])(_: WebContext)
}