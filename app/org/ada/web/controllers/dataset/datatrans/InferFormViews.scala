package org.ada.web.controllers.dataset.datatrans

import org.ada.server.models.datatrans.InferDataSetTransformation
import org.incal.play.controllers.WebContext
import views.html.{datasettrans => view}

object InferFormViews extends DataSetTransformationFormViews[InferDataSetTransformation] {

  override protected def viewElements(implicit webContext: WebContext) =
    idForm => view.inferDataSetElements(idForm.id, idForm.form)
}