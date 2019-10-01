package org.ada.web.controllers.dataset

import org.incal.play.controllers.{GenericJsRouter, GenericRouter}
import reactivemongo.bson.BSONObjectID

import scalaz.Scalaz._

/**
  * Container for various calls available for the data set controller.
  * To be passed to other modules like views to simplify access to data set actions.
  */
class DataSetRouter(dataSetId: String) extends GenericRouter(routes.DataSetDispatcher, "dataSet", dataSetId) {
  val list = routes.find _ map route
  val plainList = routeFun(_.find())
  val getView = routes.getView _ map route
  val getDefaultView = routeFun(_.getDefaultView)
  val get = routes.get _ map route
  val getScatter = routes.getScatter _ map route
  val getDistribution = routes.getDistribution _ map route
  val getCumulativeCount = routes.getCumulativeCount _ map route
  val getPearsonCorrelations = routes.getPearsonCorrelations _ map route
  val getMatthewsCorrelations = routes.getMatthewsCorrelations _ map route
  val getHeatmap = routes.getHeatmap _ map route
  val getComparison = routes.getComparison _ map route
  val getClusterization = routeFun(_.getClusterization)
  val getIndependenceTest = routes.getIndependenceTest _ map route
  val getSeriesProcessingSpec = routeFun(_.getSeriesProcessingSpec)
  val runSeriesProcessing = routeFun(_.runSeriesProcessing)
  val getSeriesTransformationSpec = routeFun(_.getSeriesTransformationSpec)
  val runSeriesTransformation = routeFun(_.runSeriesTransformation)
  val getTable = routes.getTable _ map route
  val generateTable = routes.generateTable _ map route
  val fieldNamesAndLabels = routes.getFieldNamesAndLabels _ map route
  val allFields = routeFun(_.getFields())
  val allFieldNamesAndLabels = routeFun(_.getFieldNamesAndLabels())
  val getFieldValue = routes.getFieldValue _ map route

  // scalaz package does work here (too many params probably) hence we need to name all params explicitly and forward
  val exportViewAsCsv = (dataViewId:BSONObjectID, delimiter:String, replaceEolWithSpace:Boolean, eol:Option[String], filter:Seq[org.incal.core.FilterCondition], tableColumnsOnly: Boolean, useDisplayValues: Boolean, escapeStringValues: Boolean) =>
    route(routes.exportViewRecordsAsCsv(dataViewId, delimiter, replaceEolWithSpace, eol, filter, tableColumnsOnly, useDisplayValues, escapeStringValues))
  // scalaz package does work here (too many params probably) hence we need to name all params explicitly and forward
  val exportTableAsCsv  = (tableColumnNames: Seq[String], delimiter: String, replaceEolWithSpace: Boolean, eol: Option[String], filter: Seq[org.incal.core.FilterCondition], tableColumnsOnly: Boolean, useDisplayValues: Boolean, escapeStringValues: Boolean, selectedOnly: Boolean, selectedIds: Seq[BSONObjectID]) =>
    route(routes.exportTableRecordsAsCsv(tableColumnNames, delimiter, replaceEolWithSpace, eol, filter, tableColumnsOnly, useDisplayValues, escapeStringValues, selectedOnly, selectedIds))

  val exportViewAsJson  = routes.exportViewRecordsAsJson _ map route
  val exportTableAsJson  = routes.exportTableRecordsAsJson _ map route

  val exportTranSMARTData = routeFun(_.exportTranSMARTDataFile())
  val exportTranSMARTMapping = routeFun(_.exportTranSMARTMappingFile())
  val getCategoriesWithFieldsAsTreeNodes = routes.getCategoriesWithFieldsAsTreeNodes _ map route
  val findCustom = routes.findCustom _ map route
}

final class DataSetJsRouter(dataSetId: String) extends GenericJsRouter(routes.javascript.DataSetDispatcher, "dataSet", dataSetId) {
  val getFieldValue = routeFun(_.getFieldValue)
  val getField = routeFun(_.getField)
  val getFieldTypeWithAllowedValues = routeFun(_.getFieldTypeWithAllowedValues)
  val getWidgets = routeFun(_.getWidgets)
  val getView = routeFun(_.getView)
  val calcDistribution = routeFun(_.calcDistribution)
  val calcCumulativeCount = routeFun(_.calcCumulativeCount)
  val calcScatter = routeFun(_.calcScatter)
  val calcHeatmap = routeFun(_.calcHeatmap)
  val calcComparison = routeFun(_.calcComparison)
  val cluster = routeFun(_.cluster)
  val testIndependence = routeFun(_.testIndependence)
  val calcPearsonCorrelations = routeFun(_.calcPearsonCorrelations)
  val calcMatthewsCorrelations = routeFun(_.calcMatthewsCorrelations)
  val generateTable = routeFun(_.generateTable)
  val generateTableWithFilter = routeFun(_.generateTableWithFilter)
  val getViewElementsAndWidgetsCallback = routeFun(_.getViewElementsAndWidgetsCallback)
  val getNewFilterViewElementsAndWidgetsCallback = routeFun(_.getNewFilterViewElementsAndWidgetsCallback)
  val getNewFilter = routeFun(_.getNewFilter)
}