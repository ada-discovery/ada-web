package org.ada.web.controllers.dataset

import org.ada.server.models.Filter.FilterOrId
import org.ada.server.models.{AggType, FieldTypeId}
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID
import org.incal.core.FilterCondition
import org.incal.spark_ml.models.VectorScalerType
import org.incal.play.PageOrder
import org.incal.play.controllers.ReadonlyController

trait DataSetController extends ReadonlyController[BSONObjectID] {

  def getView(
    dataViewId: BSONObjectID,
    tablePages: Seq[PageOrder],
    filterOrIds: Seq[FilterOrId],
    filterChanged: Boolean
  ): Action[AnyContent]

  def getDefaultView: Action[AnyContent]

  def getViewElementsAndWidgetsCallback(
    dataViewId: BSONObjectID,
    tableOrder: String,
    filterOrId: FilterOrId,
    oldCountDiff: Option[Int]
  ): Action[AnyContent]

  def getNewFilter: Action[AnyContent]

  def getNewFilterViewElementsAndWidgetsCallback(
    dataViewId: BSONObjectID,
    tableOrder: String,
    totalCount: Int
  ): Action[AnyContent]

  def getWidgets: Action[AnyContent]

  def getTable(
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def generateTable(
    page: Int,
    orderBy: String,
    fieldNames: Seq[String],
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def generateTableWithFilter(
    page: Int,
    orderBy: String,
    fieldNames: Seq[String],
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def getDistribution(
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def calcDistribution(
    fieldName: String,
    groupFieldName: Option[String],
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def getCumulativeCount(
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def calcCumulativeCount(
    fieldName: String,
    groupFieldName: Option[String],
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def getScatter(
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def calcScatter(
    xFieldName: String,
    yFieldName: String,
    groupOrValueFieldName: Option[String],
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def getPearsonCorrelations(
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def calcPearsonCorrelations(
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def getMatthewsCorrelations(
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def calcMatthewsCorrelations(
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def getHeatmap(
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def calcHeatmap(
    xFieldName: String,
    yFieldName: String,
    valueFieldName: Option[String],
    aggType: Option[AggType.Value],
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def getComparison(
    filterOrIds: Seq[FilterOrId]
  ): Action[AnyContent]

  def calcComparison(
    fieldName: String,
    filterOrIds: Seq[FilterOrId]
  ): Action[AnyContent]

  def getIndependenceTest(
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def testIndependence(
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def getFractalis(
    fieldName: Option[String]
  ): Action[AnyContent]

  def getClusterization: Action[AnyContent]

  def cluster(
    mlModelId: BSONObjectID,
    inputFieldNames: Seq[String],
    filterId: Option[BSONObjectID],
    featuresNormalizationType: Option[VectorScalerType.Value],
    pcaDims: Option[Int]
  ): Action[AnyContent]

  def getSeriesProcessingSpec: Action[AnyContent]

  def runSeriesProcessing: Action[AnyContent]

  def getSeriesTransformationSpec: Action[AnyContent]

  def runSeriesTransformation: Action[AnyContent]

  def getFieldNamesAndLabels(
    fieldTypeIds: Seq[FieldTypeId.Value]
  ): Action[AnyContent]

  def getFields(
    fieldTypeIds: Seq[FieldTypeId.Value]
  ): Action[AnyContent]

  def getFieldValue(id: BSONObjectID, fieldName: String): Action[AnyContent]

  def getField(fieldName: String): Action[AnyContent]

  def getFieldTypeWithAllowedValues(fieldName: String): Action[AnyContent]

  def exportViewRecordsAsCsv(
    dataViewId: BSONObjectID,
    delimiter: String,
    replaceEolWithSpace: Boolean,
    eol: Option[String],
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean,
    useDisplayValues: Boolean,
    escapeStringValues: Boolean
  ): Action[AnyContent]

  def exportTableRecordsAsCsv(
    tableColumnNames: Seq[String],
    delimiter: String,
    replaceEolWithSpace: Boolean,
    eol: Option[String],
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean,
    useDisplayValues: Boolean,
    escapeStringValues: Boolean,
    selectedOnly: Boolean,
    selectedIds: Seq[BSONObjectID]
  ): Action[AnyContent]

  def exportViewRecordsAsJson(
    dataViewId: BSONObjectID,
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean,
    useDisplayValues: Boolean
  ): Action[AnyContent]

  def exportTableRecordsAsJson(
    tableColumnNames: Seq[String],
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean,
    useDisplayValues: Boolean,
    selectedOnly: Boolean,
    selectedIds: Seq[BSONObjectID]
  ): Action[AnyContent]

  def exportTranSMARTDataFile(
    delimiter: String,
    visitFieldName: Option[String],
    replaceEolWithSpace: Boolean
  ): Action[AnyContent]

  def exportTranSMARTMappingFile(
    delimiter: String,
    visitFieldName: Option[String],
    replaceEolWithSpace: Boolean
  ): Action[AnyContent]

  def getCategoriesWithFieldsAsTreeNodes(filterOrId: FilterOrId): Action[AnyContent]

  def findCustom(
    filterOrId: FilterOrId,
    orderBy: String,
    projection: Seq[String],
    limit: Option[Int],
    skip: Option[Int]
  ): Action[AnyContent]
}