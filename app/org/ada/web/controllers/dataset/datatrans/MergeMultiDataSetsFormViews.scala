package org.ada.web.controllers.dataset.datatrans

import org.ada.server.models.datatrans.MergeMultiDataSetsTransformation
import org.ada.web.controllers.core.SeqOptionFormatter
import org.incal.play.controllers.WebContext
import play.api.data.Forms.{nonEmptyText, of, seq}
import views.html.{datasettrans => view}

object MergeMultiDataSetsFormViews extends DataSetTransformationFormViews[MergeMultiDataSetsTransformation] {

  private implicit val seqOptionFormatter = SeqOptionFormatter.apply

  override protected val extraMappings =
    Seq(
      "sourceDataSetIds" -> seq(nonEmptyText),
      "fieldNameMappings" -> seq(of[Seq[Option[String]]]).verifying(
        "At least one field mapping must be provided.",
        mappings => mappings.nonEmpty
      )
    )

  override protected def viewElements(implicit webContext: WebContext) =
    idForm => view.mergeMultiDataSetsElements(idForm.id, idForm.form)
}