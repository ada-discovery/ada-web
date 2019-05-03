package org.ada.web.controllers.dataset

import javax.inject.Inject

import org.incal.play.controllers.SecureControllerDispatcher
import org.ada.server.models.Filter.FilterOrId
import org.ada.server.models.{AggType, FieldTypeId}
import reactivemongo.bson.BSONObjectID
import org.incal.play.security.{AuthAction, SecurityRole}
import org.ada.web.models.security.DataSetPermission
import org.incal.core.FilterCondition
import org.incal.spark_ml.models.VectorScalerType
import org.incal.play.PageOrder
import play.api.mvc.{Action, AnyContent}

class DataSetDispatcher @Inject() (
  dscf: DataSetControllerFactory
) extends SecureControllerDispatcher[DataSetController]("dataSet")
    with DataSetController {

  override protected def getController(id: String) =
    dscf(id).getOrElse(
      throw new IllegalArgumentException(s"Controller id '${id}' not recognized.")
    )

  override protected def getAllowedRoleGroups(
    controllerId: String,
    actionName: String
  ) = List(Array(SecurityRole.admin))

  override protected def getPermission(
    controllerId: String,
    actionName: String
  ) = Some(DataSetPermission(controllerId, ControllerName.dataSet, actionName))

  override def get(id: BSONObjectID) = dispatch(_.get(id))

  override def find(
    page: Int,
    orderBy: String,
    filter: Seq[FilterCondition]
  ) = dispatch(_.find(page, orderBy, filter))

  override def listAll(orderBy: String) = dispatch(_.listAll(orderBy))

  override def getView(
    dataViewId: BSONObjectID,
    tablePages: Seq[PageOrder],
    filterOrIds: Seq[FilterOrId],
    filterChanged: Boolean
  ) = dispatch(_.getView(dataViewId, tablePages, filterOrIds, filterChanged))

  override def getDefaultView = dispatch(_.getDefaultView)

  override def getViewElementsAndWidgetsCallback(
    dataViewId: BSONObjectID,
    tableOrder: String,
    filterOrId: FilterOrId,
    oldCountDiff: Option[Int]
  ) = dispatchAjax(_.getViewElementsAndWidgetsCallback(dataViewId, tableOrder, filterOrId, oldCountDiff))

  override def getNewFilterViewElementsAndWidgetsCallback(
    dataViewId: BSONObjectID,
    tableOrder: String,
    totalCount: Int
  ) = dispatchAjax(_.getNewFilterViewElementsAndWidgetsCallback(dataViewId, tableOrder, totalCount))

  override def getNewFilter  = dispatchAjax(_.getNewFilter)

  override def getTable(
    filterOrId: FilterOrId
  ) = dispatch(_.getTable(filterOrId))

  override def generateTable(
    page: Int,
    orderBy: String,
    fieldNames: Seq[String],
    filterOrId: FilterOrId
  ) = dispatchAjax(_.generateTable(page, orderBy, fieldNames, filterOrId))

  def generateTableWithFilter(
    page: Int,
    orderBy: String,
    fieldNames: Seq[String],
    filterOrId: FilterOrId
  ) = dispatchAjax(_.generateTableWithFilter(page, orderBy, fieldNames, filterOrId))

  override def getWidgets = dispatchAjax(_.getWidgets)

  override def getDistribution(
    filterOrId: FilterOrId
  ) = dispatch(_.getDistribution(filterOrId))

  override def calcDistribution(
    fieldName: String,
    groupFieldName: Option[String],
    filterOrId: FilterOrId
  ) = dispatchAjax(_.calcDistribution(fieldName, groupFieldName, filterOrId))

  override def getCumulativeCount(
    filterOrId: FilterOrId
  ) = dispatch(_.getCumulativeCount(filterOrId))

  override def calcCumulativeCount(
    fieldName: String,
    groupFieldName: Option[String],
    filterOrId: FilterOrId
  ) = dispatchAjax(_.calcCumulativeCount(fieldName, groupFieldName, filterOrId))

  override def getScatter(
    filterOrId: FilterOrId
  ) = dispatch(_.getScatter(filterOrId))

  override def calcScatter(
    xFieldName: String,
    yFieldName: String,
    groupOrValueFieldName: Option[String],
    filterOrId: FilterOrId
  ) = dispatchAjax(_.calcScatter(xFieldName, yFieldName, groupOrValueFieldName, filterOrId))

  override def getPearsonCorrelations(
    filterOrId: FilterOrId
  ) = dispatch(_.getPearsonCorrelations(filterOrId))

  override def calcPearsonCorrelations(
    filterOrId: FilterOrId
  ) = dispatchAjax(_.calcPearsonCorrelations(filterOrId))

  override def getMatthewsCorrelations(
    filterOrId: FilterOrId
  ) = dispatch(_.getMatthewsCorrelations(filterOrId))

  override def calcMatthewsCorrelations(
    filterOrId: FilterOrId
  ) = dispatchAjax(_.calcMatthewsCorrelations(filterOrId))

  override def getHeatmap(
    filterOrId: FilterOrId
  ) = dispatch(_.getHeatmap(filterOrId))

  override def calcHeatmap(
    xFieldName: String,
    yFieldName: String,
    valueFieldName: Option[String],
    aggType: Option[AggType.Value],
    filterOrId: FilterOrId
  ) = dispatchAjax(_.calcHeatmap(xFieldName, yFieldName, valueFieldName, aggType, filterOrId))

  override def getComparison(
    filterOrIds: Seq[FilterOrId]
  ) = dispatch(_.getComparison(filterOrIds))

  override def calcComparison(
    fieldName: String,
    filterOrIds: Seq[FilterOrId]
  ) = dispatchAjax(_.calcComparison(fieldName, filterOrIds))

  override def getIndependenceTest(
    filterOrId: FilterOrId
  ) = dispatch(_.getIndependenceTest(filterOrId))

  override def testIndependence(
    filterOrId: FilterOrId
  ) = dispatchAjax(_.testIndependence(filterOrId))

  override def getFractalis(
    fieldNameOption: Option[String]
  ) = dispatch(_.getFractalis(fieldNameOption))

  override def getClusterization = dispatch(_.getClusterization)

  override def cluster(
    mlModelId: BSONObjectID,
    inputFieldNames: Seq[String],
    filterId: Option[BSONObjectID],
    featuresNormalizationType: Option[VectorScalerType.Value],
    pcaDims: Option[Int]
  ) = dispatchAjax(_.cluster(mlModelId, inputFieldNames, filterId, featuresNormalizationType, pcaDims))

  override def getSeriesProcessingSpec = dispatch(_.getSeriesProcessingSpec)

  override def runSeriesProcessing = dispatch(_.runSeriesProcessing)

  override def getSeriesTransformationSpec = dispatch(_.getSeriesTransformationSpec)

  override def runSeriesTransformation = dispatch(_.runSeriesTransformation)

  // field stuff

  override def getField(fieldName: String) = dispatchAjax(_.getField(fieldName))

  override def getFieldNamesAndLabels(
    fieldTypeIds: Seq[FieldTypeId.Value]
  ) = dispatchAjax(_.getFieldNamesAndLabels(fieldTypeIds))

  override def getFieldTypeWithAllowedValues(
    fieldName: String
  ) = dispatchAjax(_.getFieldTypeWithAllowedValues(fieldName))

  override def getFields(
    fieldTypeIds: Seq[FieldTypeId.Value]
  ) = dispatchAjax(_.getFields(fieldTypeIds))

  override def getFieldValue(
    id: BSONObjectID,
    fieldName: String
  ) = dispatchAjax(_.getFieldValue(id, fieldName))

  override def getCategoriesWithFieldsAsTreeNodes(
    filterOrId: FilterOrId
  ) = dispatchAjax(_.getCategoriesWithFieldsAsTreeNodes(filterOrId))


  // export

  override def exportViewRecordsAsCsv(
    dataViewId: BSONObjectID,
    delimiter: String,
    replaceEolWithSpace: Boolean,
    eol: Option[String],
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean,
    useDisplayValues: Boolean,
    escapeStringValues: Boolean
  ) = dispatch(_.exportViewRecordsAsCsv(dataViewId, delimiter, replaceEolWithSpace, eol, filter, tableColumnsOnly, useDisplayValues, escapeStringValues))

  override def exportTableRecordsAsCsv(
    tableColumnNames: Seq[String],
    delimiter: String,
    replaceEolWithSpace: Boolean,
    eol: Option[String],
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean,
    useDisplayValues: Boolean,
    escapeStringValues: Boolean
  ) = dispatch(_.exportTableRecordsAsCsv(tableColumnNames, delimiter, replaceEolWithSpace, eol, filter, tableColumnsOnly, useDisplayValues, escapeStringValues))

  override def exportViewRecordsAsJson(
    dataViewId: BSONObjectID,
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean,
    useDisplayValues: Boolean
  ) = dispatch(_.exportViewRecordsAsJson(dataViewId, filter, tableColumnsOnly, useDisplayValues))

  override def exportTableRecordsAsJson(
    tableColumnNames: Seq[String],
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean,
    useDisplayValues: Boolean
  ) = dispatch(_.exportTableRecordsAsJson(tableColumnNames, filter, tableColumnsOnly, useDisplayValues))

  override def exportTranSMARTDataFile(
    delimiter: String,
    visitFieldName: Option[String],
    replaceEolWithSpace: Boolean
  ) = dispatch(_.exportTranSMARTDataFile(delimiter, visitFieldName, replaceEolWithSpace))

  override def exportTranSMARTMappingFile(
    delimiter: String,
    visitFieldName: Option[String],
    replaceEolWithSpace: Boolean
  ) = dispatch(_.exportTranSMARTMappingFile(delimiter, visitFieldName, replaceEolWithSpace))

  // api function

  override def findCustom(
    filterOrId: Either[Seq[FilterCondition], BSONObjectID],
    orderBy: String,
    projection: Seq[String],
    limit: Option[Int],
    skip: Option[Int]
  ) = dispatch(_.findCustom(filterOrId, orderBy, projection, limit, skip))
}