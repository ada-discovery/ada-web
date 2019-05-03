package org.ada.web.controllers.dataset

import javax.inject.Inject
import be.objectify.deadbolt.scala.DeadboltHandler
import org.ada.web.controllers.core.AdminOrOwnerControllerDispatcherExt
import org.ada.server.AdaException
import org.ada.server.models.{AggType, CorrelationType}
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.mvc.{Action, AnyContent, Request}
import reactivemongo.bson.BSONObjectID
import org.ada.web.security.AdaAuthConfig
import org.incal.core.FilterCondition
import org.ada.server.services.UserManager

import scala.concurrent.ExecutionContext.Implicits.global

class DataViewDispatcher @Inject()(
  val dscf: DataSetControllerFactory,
  factory: DataViewControllerFactory,
  dsaf: DataSetAccessorFactory,
  val userManager: UserManager
) extends DataSetLikeDispatcher[DataViewController](ControllerName.dataview)
  with AdminOrOwnerControllerDispatcherExt[DataViewController]
  with DataViewController
  with AdaAuthConfig {

  override def controllerFactory = factory(_)

  override def get(id: BSONObjectID) = dispatchIsAdminOrOwner(id, _.get(id))

  override def find(page: Int, orderBy: String, filter: Seq[FilterCondition]) = dispatch(_.find(page, orderBy, filter))

  override def listAll(orderBy: String) = dispatch(_.listAll(orderBy))

  override def create = dispatch(_.create)

  override def update(id: BSONObjectID) = dispatchIsAdminOrOwner(id, _.update(id))

  override def edit(id: BSONObjectID) = dispatchIsAdminOrOwner(id, _.edit(id))

  override def delete(id: BSONObjectID) = dispatchIsAdminOrOwner(id, _.delete(id))

  override def save = dispatch(_.save)

  override def idAndNames = dispatchIsAdmin(_.idAndNames)

  override def idAndNamesAccessible = dispatchAjax(_.idAndNamesAccessible)

  override def getAndShowView(id: BSONObjectID) = dispatchIsAdminOrOwner(id, _.getAndShowView(id))

  override def updateAndShowView(id: BSONObjectID) = dispatchIsAdminOrOwner(id, _.updateAndShowView(id))

  override def copy(id: BSONObjectID) = dispatch(_.copy(id))

  override def addDistributions(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = dispatchIsAdminOrOwnerAjax(dataViewId, _.addDistributions(dataViewId, fieldNames))

  override def addDistribution(
    dataViewId: BSONObjectID,
    fieldName: String,
    groupFieldName: Option[String]
  ) = dispatchIsAdminOrOwnerAjax(dataViewId, _.addDistribution(dataViewId, fieldName, groupFieldName))

  override def addCumulativeCounts(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = dispatchIsAdminOrOwnerAjax(dataViewId, _.addCumulativeCounts(dataViewId, fieldNames))

  override def addCumulativeCount(
    dataViewId: BSONObjectID,
    fieldName: String,
    groupFieldName: Option[String]
  ) = dispatchIsAdminOrOwnerAjax(dataViewId, _.addCumulativeCount(dataViewId, fieldName, groupFieldName))

  override def addTableFields(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = dispatchIsAdminOrOwnerAjax(dataViewId, _.addTableFields(dataViewId, fieldNames))

  override def addCorrelation(
    dataViewId: BSONObjectID,
    correlationType: CorrelationType.Value
  ) = dispatchIsAdminOrOwnerAjax(dataViewId, _.addCorrelation(dataViewId, correlationType))

  override def addScatter(
    dataViewId: BSONObjectID,
    xFieldName: String,
    yFieldName: String,
    groupOrValueFieldName: Option[String]
  ) = dispatchIsAdminOrOwnerAjax(dataViewId, _.addScatter(dataViewId, xFieldName, yFieldName, groupOrValueFieldName))

  def addHeatmap(
    dataViewId: BSONObjectID,
    xFieldName: String,
    yFieldName: String,
    valueFieldName: String,
    aggType: AggType.Value
  ) = dispatchIsAdminOrOwnerAjax(dataViewId, _.addHeatmap(dataViewId, xFieldName, yFieldName, valueFieldName, aggType))

  def addGridDistribution(
    dataViewId: BSONObjectID,
    xFieldName: String,
    yFieldName: String
  ) = dispatchIsAdminOrOwnerAjax(dataViewId, _.addGridDistribution(dataViewId, xFieldName, yFieldName))

  override def addBoxPlots(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = dispatchIsAdminOrOwnerAjax(dataViewId, _.addBoxPlots(dataViewId, fieldNames))

  override def addBoxPlot(
    dataViewId: BSONObjectID,
    fieldName: String,
    groupFieldName: Option[String]
  ) = dispatchIsAdminOrOwnerAjax(dataViewId, _.addBoxPlot(dataViewId, fieldName, groupFieldName))

  override def addBasicStats(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = dispatchIsAdminOrOwnerAjax(dataViewId, _.addBasicStats(dataViewId, fieldNames))

  override def addIndependenceTest(
    dataViewId: BSONObjectID,
    targetFieldName: String
  ) = dispatchIsAdminOrOwnerAjax(dataViewId, _.addIndependenceTest(dataViewId, targetFieldName))

  override def saveFilter(
    dataViewId: BSONObjectID,
    filterOrIds: Seq[Either[Seq[FilterCondition], BSONObjectID]]
  ) = dispatchIsAdminOrOwnerAjax(dataViewId, _.saveFilter(dataViewId, filterOrIds))

  protected def dispatchIsAdminOrOwner(
    id: BSONObjectID,
    action: DataViewController => Action[AnyContent]
  ): Action[AnyContent] =
    dispatchIsAdminOrOwnerAux(id, action, None)

  protected def dispatchIsAdminOrOwnerAjax(
    id: BSONObjectID,
    action: DataViewController => Action[AnyContent]
  ): Action[AnyContent] =
    dispatchIsAdminOrOwnerAux(id, action, Some(unauthorizedDeadboltHandler))

  protected def dispatchIsAdminOrOwnerAux(
    id: BSONObjectID,
    action: DataViewController => Action[AnyContent],
    outputDeadboltHandler: Option[DeadboltHandler]
  ): Action[AnyContent] = {

    val objectOwnerFun = {
      request: Request[AnyContent] =>
        val dataSetId = getControllerId(request)
        val dsa = dsaf(dataSetId).getOrElse(throw new AdaException(s"Data set id $dataSetId not found."))
        dsa.dataViewRepo.get(id).map { dataView =>
          dataView.flatMap(_.createdById)
        }
      }

    dispatchIsAdminOrOwnerAux(objectOwnerFun, outputDeadboltHandler)(action)
  }
}