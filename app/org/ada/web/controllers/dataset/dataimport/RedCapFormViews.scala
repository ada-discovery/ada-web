package org.ada.web.controllers.dataset.dataimport

import org.ada.server.models.dataimport.RedCapDataSetImport
import org.ada.server.models.{DataSetSetting, StorageType}
import org.incal.play.controllers.WebContext
import play.api.data.Form
import play.api.data.Forms._
import views.html.{datasetimport => view}

object RedCapFormViews extends DataSetImportFormViews[RedCapDataSetImport] {

  override protected val imagePath = Some("images/logos/redcap.jpg")
  override protected val imageLink = Some("https://www.project-redcap.org")

  override protected val extraMappings = Seq(
//    "url" -> nonEmptyText,
//    "token" -> nonEmptyText,
//    "importDictionaryFlag" -> boolean,
//    "eventNames" -> of[Seq[String]],
//    "categoriesToInheritFromFirstVisit" -> of[Seq[String]],
    "saveBatchSize" -> optional(number(min = 1))
  )

  override protected val viewElements =
    view.redCapTypeElements(_: Form[RedCapDataSetImport])(_: WebContext)

  override protected val defaultCreateInstance =
    Some(() => RedCapDataSetImport(
      dataSpaceName = "",
      dataSetId = "",
      dataSetName = "",
      url = "",
      token = "",
      importDictionaryFlag = true,
      saveBatchSize = Some(10),
      setting = Some(new DataSetSetting("", StorageType.ElasticSearch))
    ))
}
