package org.ada.web.controllers.dataset.dataimport

import org.ada.server.models.{DataSetSetting, StorageType}
import org.ada.server.models.dataimport.EGaitDataSetImport
import org.incal.play.controllers.WebContext
import play.api.data.Form
import views.html.{datasetimport => view}

object EGaitFormViews extends DataSetImportFormViews[EGaitDataSetImport] {

  override protected[controllers] val displayName = "eGaIT"

  override protected val imagePath = Some("images/logos/egait.png")
  override protected val imageLink = Some("https://www.astrum-it.de")

  override protected val viewElements =
    view.eGaitTypeElements(_: Form[EGaitDataSetImport])(_: WebContext)

  override protected val defaultCreateInstance =
    Some(() => EGaitDataSetImport(
      dataSpaceName = "",
      dataSetId = "",
      dataSetName = "",
      importRawData = false,
      setting = Some(new DataSetSetting("", StorageType.ElasticSearch))
    ))
}