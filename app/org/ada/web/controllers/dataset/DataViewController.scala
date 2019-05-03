package org.ada.web.controllers.dataset

import org.ada.server.models.{AggType, CorrelationType}
import org.incal.core.FilterCondition
import org.incal.play.controllers.CrudController
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID

trait DataViewController extends CrudController[BSONObjectID] {

  def idAndNames: Action[AnyContent]

  def idAndNamesAccessible: Action[AnyContent]

  def getAndShowView(id: BSONObjectID): Action[AnyContent]

  def updateAndShowView(id: BSONObjectID): Action[AnyContent]

  def copy(id: BSONObjectID): Action[AnyContent]

  def addDistributions(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ): Action[AnyContent]

  def addDistribution(
    dataViewId: BSONObjectID,
    fieldName: String,
    groupName: Option[String]
  ): Action[AnyContent]

  def addCumulativeCount(
    dataViewId: BSONObjectID,
    fieldName: String,
    groupName: Option[String]
  ): Action[AnyContent]

  def addCumulativeCounts(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ): Action[AnyContent]

  def addBoxPlots(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ): Action[AnyContent]

  def addBoxPlot(
    dataViewId: BSONObjectID,
    fieldName: String,
    groupFieldName: Option[String]
  ): Action[AnyContent]

  def addBasicStats(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ): Action[AnyContent]

  def addScatter(
    dataViewId: BSONObjectID,
    xFieldName: String,
    yFieldName: String,
    groupOrValueFieldName: Option[String]
  ): Action[AnyContent]

  def addCorrelation(
    dataViewId: BSONObjectID,
    correlationType: CorrelationType.Value
  ): Action[AnyContent]

  def addHeatmap(
    dataViewId: BSONObjectID,
    xFieldName: String,
    yFieldName: String,
    valueFieldName: String,
    aggType: AggType.Value
  ): Action[AnyContent]

  def addGridDistribution(
    dataViewId: BSONObjectID,
    xFieldName: String,
    yFieldName: String
  ): Action[AnyContent]

  def addIndependenceTest(
    dataViewId: BSONObjectID,
    targetFieldName: String
  ): Action[AnyContent]

  def addTableFields(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ): Action[AnyContent]

  def saveFilter(
    dataViewId: BSONObjectID,
    filterOrIds: Seq[Either[Seq[FilterCondition], BSONObjectID]]
  ): Action[AnyContent]
}