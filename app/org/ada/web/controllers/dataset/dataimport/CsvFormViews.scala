package org.ada.web.controllers.dataset.dataimport

import org.ada.server.models.{DataSetSetting, StorageType}
import org.ada.server.models.dataimport.CsvDataSetImport
import org.incal.play.controllers.WebContext
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.format.Formats._
import views.html.{datasetimport => view}

object CsvFormViews extends DataSetImportFormViews[CsvDataSetImport] {

  override protected[controllers] val displayName = "CSV"

  override protected val imagePath = Some("images/logos/csv_100.png")

  override protected val extraMappings =
    Seq(
      "delimiter" -> default(nonEmptyText, ","),
      "inferenceMaxEnumValuesCount" -> optional(number(min = 1)),
      "inferenceMinAvgValuesPerEnum" -> optional(of[Double]).verifying("Must be positive", _.map(_ > 0).getOrElse(true)),
      "saveBatchSize" -> optional(number(min = 1))
    )

  override protected val viewElements =
    view.csvTypeElements(_: Form[CsvDataSetImport])(_: WebContext)

  override protected val defaultCreateInstance =
    Some(() => CsvDataSetImport(
      dataSpaceName = "",
      dataSetId = "",
      dataSetName = "",
      delimiter  = "",
      matchQuotes = false,
      inferFieldTypes = true,
      saveBatchSize = Some(10),
      setting = Some(new DataSetSetting("", StorageType.ElasticSearch))
    ))
}