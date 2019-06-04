package org.ada.web.controllers.dataset.dataimport

import org.ada.server.models.dataimport.JsonDataSetImport
import org.ada.server.models.{DataSetSetting, StorageType}
import org.incal.play.controllers.WebContext
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.format.Formats._
import views.html.{datasetimport => view}

object JsonFormViews extends DataSetImportFormViews[JsonDataSetImport] {

  override protected val imagePath = Some("images/logos/json_100.png")

  override protected val extraMappings = Seq(
    "inferenceMaxEnumValuesCount" -> optional(number(min = 1)),
    "inferenceMinAvgValuesPerEnum" -> optional(of[Double]).verifying("Must be positive", _.map(_ > 0).getOrElse(true)),
    "saveBatchSize" -> optional(number(min = 1))
  )

  override protected val viewElements =
    view.jsonTypeElements(_: Form[JsonDataSetImport])(_: WebContext)

  override protected val defaultCreateInstance =
    Some(() => JsonDataSetImport(
      dataSpaceName = "",
      dataSetId = "",
      dataSetName = "",
      inferFieldTypes = true,
      saveBatchSize = Some(10),
      setting = Some(new DataSetSetting("", StorageType.ElasticSearch))
    ))
}
