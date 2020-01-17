package org.ada.web.controllers.dataset

import org.ada.server.models.Filter.FilterOrId
import org.ada.server.models.{AggType, FieldTypeId}
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID
import org.incal.core.FilterCondition
import org.incal.spark_ml.models.VectorScalerType
import org.incal.play.PageOrder
import org.incal.play.controllers.ReadonlyController

/**
  * The most important controller trait in Ada defining functionality of each data set, in particular its "data" presentation part.
  *
  * It contains actions to handle:
  * views (<code>getView</code>, <code>getViewElementsAndWidgetsCallback</code>),
  * analytics (<code>getDistribution</code>, <code>calcDistribution</code>, <code>calcPearsonCorrelations</code>),
  * and exporting (<code>exportViewRecordsAsCsv</code>, <code>exportTableRecordsAsJson</code>).
  *
  * To access/call the actions two routes are available: <code>DataSetRouter</code> and <code>DataSetJsRouter</code>.
  * These can be used typically through the web context passed around as an implicit (<code>DataSetWebContext</code>).
  *
  * Handling of the access permissions and dispatching based on a provided data set id is done in <code>DataSetDispatcher</code>,
  * which by default uses a default implementation <code>DataSetControllerImpl</code> unless specified otherwise.
  *
  * Note that each associated meta-data type has its own controller such as <code>CategoryController</code> and <code>DictionaryController</code>,
  * which are linked to the data set controller by a data set id.
  *
  * @since 2016
  */
trait DataSetController extends ReadonlyController[BSONObjectID] {

  /**
    * Gets a redirect to a default view for the data set associated with this controller. Default view is flagged with "default: true".
    * If more than one exists redirects to the first one.
    *
    * @return
    */
  def getDefaultView: Action[AnyContent]

  def getView(
    dataViewId: BSONObjectID,
    tablePages: Seq[PageOrder],
    filterOrIds: Seq[FilterOrId],
    filterChanged: Boolean
  ): Action[AnyContent]

  def getViewElementsAndWidgetsCallback(
    dataViewId: BSONObjectID,
    tableOrder: String,
    filterOrId: FilterOrId,
    oldCountDiff: Option[Int],
    tableSelection: Boolean
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
    filterOrId: FilterOrId,
    tableSelection: Boolean
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

  def getClusterization: Action[AnyContent]

  def cluster(
    mlModelId: BSONObjectID,
    inputFieldNames: Seq[String],
    filterId: Option[BSONObjectID],
    featuresNormalizationType: Option[VectorScalerType.Value],
    pcaDims: Option[Int]
  ): Action[AnyContent]

  @Deprecated // turn into a transformation
  def getSeriesProcessingSpec: Action[AnyContent]

  @Deprecated // turn into a transformation
  def runSeriesProcessing: Action[AnyContent]

  @Deprecated // turn into a transformation
  def getSeriesTransformationSpec: Action[AnyContent]

  @Deprecated // turn into a transformation
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