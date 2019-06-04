package org.ada.web.controllers.dataset.dataimport

import org.ada.server.models.dataimport.SynapseDataSetImport
import org.ada.server.models.{DataSetSetting, StorageType}
import org.incal.play.controllers.WebContext
import play.api.data.Form
import play.api.data.Forms._
import views.html.{datasetimport => view}

object SynapseFormViews extends DataSetImportFormViews[SynapseDataSetImport] {

  override protected val imagePath = Some("images/logos/synapse.png")
  override protected val imageLink = Some("https://www.synapse.org")

  override protected val extraMappings = Seq(
    "batchSize" -> optional(number(min = 1)),
    "bulkDownloadGroupNumber" -> optional(number(min = 1))
  )

  override protected val viewElements =
    view.synapseTypeElements(_: Form[SynapseDataSetImport])(_: WebContext)

  override protected val defaultCreateInstance =
    Some(() => SynapseDataSetImport(
      dataSpaceName = "",
      dataSetId = "",
      dataSetName = "",
      tableId = "",
      downloadColumnFiles = false,
      batchSize = Some(10),
      setting = Some(new DataSetSetting("", StorageType.ElasticSearch))
    ))
}
