package org.ada.web.controllers.dataset.datatrans

import org.ada.server.models.datatrans.FilterDataSetTransformation
import org.incal.play.controllers.WebContext
import views.html.{datasettrans => view}

object FilterFormViews extends DataSetTransformationFormViews[FilterDataSetTransformation] {

  override protected def viewElements(implicit webContext: WebContext) =
    idForm => view.filterDataSetElements(idForm.id, idForm.form)
}