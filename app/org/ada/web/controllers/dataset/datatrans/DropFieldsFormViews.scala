package org.ada.web.controllers.dataset.datatrans

import org.ada.server.models.datatrans.{CopyDataSetTransformation, DropFieldsTransformation}
import org.incal.play.controllers.WebContext
import play.api.data.Form
import views.html.{datasettrans => view}

object DropFieldsFormViews extends DataSetTransformationFormViews[DropFieldsTransformation] {

  override protected def viewElements(implicit webContext: WebContext) =
    idForm => view.dropFieldsElements(idForm.id, idForm.form)
}