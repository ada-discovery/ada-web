package org.ada.web.controllers.dataset.datatrans

import org.ada.server.models.datatrans.CopyDataSetTransformation
import org.incal.play.controllers.WebContext
import views.html.{datasettrans => view}

object CopyFormViews extends DataSetTransformationFormViews[CopyDataSetTransformation] {

  override protected def viewElements(implicit webContext: WebContext) =
    idForm => view.copyDataSetElements(idForm.id, idForm.form)
}