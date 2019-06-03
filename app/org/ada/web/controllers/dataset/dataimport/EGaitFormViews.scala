package org.ada.web.controllers.dataset.dataimport

import org.ada.server.models.dataimport.EGaitDataSetImport
import org.incal.play.controllers.WebContext
import play.api.data.Form
import views.html.{datasetimport => view}

object EGaitFormViews extends DataSetImportFormViews[EGaitDataSetImport] {

  override protected val displayName =
    "eGait Data Set Import"

  override protected val viewElements =
    view.eGaitTypeElements(_: Form[EGaitDataSetImport])(_: WebContext)
}