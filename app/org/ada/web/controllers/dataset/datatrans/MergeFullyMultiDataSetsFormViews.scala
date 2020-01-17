package org.ada.web.controllers.dataset.datatrans

import org.ada.server.models.datatrans.MergeFullyMultiDataSetsTransformation
import org.incal.play.controllers.WebContext
import play.api.data.Forms.{nonEmptyText, seq}
import views.html.{datasettrans => view}

object MergeFullyMultiDataSetsFormViews extends DataSetTransformationFormViews[MergeFullyMultiDataSetsTransformation] {

  override protected val extraMappings =
    Seq(
      "sourceDataSetIds" -> seq(nonEmptyText)
    )

  override protected def viewElements(implicit webContext: WebContext) =
    idForm => view.mergeFullyMultiDataSetsElements(idForm.id, idForm.form)
}