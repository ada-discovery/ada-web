package org.ada.web.controllers.dataset

import be.objectify.deadbolt.scala.DeadboltHandler
import javax.inject.Inject
import org.ada.web.controllers.core.AdminOrOwnerControllerDispatcherExt
import org.ada.server.AdaException
import org.ada.server.models.{AggType, CorrelationType}
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.mvc.{Action, AnyContent, Request}
import reactivemongo.bson.BSONObjectID
import org.incal.core.FilterCondition
import org.ada.server.services.UserManager
import play.api.routing.Router.Tags.RouteActionMethod

import scala.concurrent.ExecutionContext.Implicits.global

class DataViewDispatcher @Inject()(
  val dscf: DataSetControllerFactory,
  factory: DataViewControllerFactory,
  dsaf: DataSetAccessorFactory
) extends DataSetLikeDispatcher[DataViewController](ControllerName.dataview)
  with AdminOrOwnerControllerDispatcherExt[DataViewController]
  with DataViewController {

  override protected val noCaching = true

  override def controllerFactory = factory(_)

  override def get(id: BSONObjectID) = dispatchIsAdminOrPermissionAndOwner(id, _.get(id))

  override def find(page: Int, orderBy: String, filter: Seq[FilterCondition]) = dispatch(_.find(page, orderBy, filter))

  override def listAll(orderBy: String) = dispatch(_.listAll(orderBy))

  override def create = dispatch(_.create)

  override def update(id: BSONObjectID) = dispatchIsAdminOrPermissionAndOwner(id, _.update(id))

  override def edit(id: BSONObjectID) = dispatchIsAdminOrPermissionAndOwner(id, _.edit(id))

  override def delete(id: BSONObjectID) = dispatchIsAdminOrPermissionAndOwner(id, _.delete(id))

  override def save = dispatch(_.save)

  override def idAndNames = dispatchIsAdmin(_.idAndNames)

  override def idAndNamesAccessible = dispatchAjax(_.idAndNamesAccessible)

  override def getAndShowView(id: BSONObjectID) = dispatchIsAdminOrPermissionAndOwner(id, _.getAndShowView(id))

  override def updateAndShowView(id: BSONObjectID) = dispatchIsAdminOrPermissionAndOwner(id, _.updateAndShowView(id))

  override def copy(id: BSONObjectID) = dispatch(_.copy(id))

  override def addDistributions(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = dispatchIsAdminOrPermissionAndOwnerAjax(dataViewId, _.addDistributions(dataViewId, fieldNames))

  override def addDistribution(
    dataViewId: BSONObjectID,
    fieldName: String,
    groupFieldName: Option[String]
  ) = dispatchIsAdminOrPermissionAndOwnerAjax(dataViewId, _.addDistribution(dataViewId, fieldName, groupFieldName))

  override def addCumulativeCounts(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = dispatchIsAdminOrPermissionAndOwnerAjax(dataViewId, _.addCumulativeCounts(dataViewId, fieldNames))

  override def addCumulativeCount(
    dataViewId: BSONObjectID,
    fieldName: String,
    groupFieldName: Option[String]
  ) = dispatchIsAdminOrPermissionAndOwnerAjax(dataViewId, _.addCumulativeCount(dataViewId, fieldName, groupFieldName))

  override def addTableFields(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = dispatchIsAdminOrPermissionAndOwnerAjax(dataViewId, _.addTableFields(dataViewId, fieldNames))

  override def addCorrelation(
    dataViewId: BSONObjectID,
    correlationType: CorrelationType.Value
  ) = dispatchIsAdminOrPermissionAndOwnerAjax(dataViewId, _.addCorrelation(dataViewId, correlationType))

  override def addScatter(
    dataViewId: BSONObjectID,
    xFieldName: String,
    yFieldName: String,
    groupOrValueFieldName: Option[String]
  ) = dispatchIsAdminOrPermissionAndOwnerAjax(dataViewId, _.addScatter(dataViewId, xFieldName, yFieldName, groupOrValueFieldName))

  def addHeatmap(
    dataViewId: BSONObjectID,
    xFieldName: String,
    yFieldName: String,
    valueFieldName: String,
    aggType: AggType.Value
  ) = dispatchIsAdminOrPermissionAndOwnerAjax(dataViewId, _.addHeatmap(dataViewId, xFieldName, yFieldName, valueFieldName, aggType))

  def addGridDistribution(
    dataViewId: BSONObjectID,
    xFieldName: String,
    yFieldName: String
  ) = dispatchIsAdminOrPermissionAndOwnerAjax(dataViewId, _.addGridDistribution(dataViewId, xFieldName, yFieldName))

  override def addBoxPlots(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = dispatchIsAdminOrPermissionAndOwnerAjax(dataViewId, _.addBoxPlots(dataViewId, fieldNames))

  override def addBoxPlot(
    dataViewId: BSONObjectID,
    fieldName: String,
    groupFieldName: Option[String]
  ) = dispatchIsAdminOrPermissionAndOwnerAjax(dataViewId, _.addBoxPlot(dataViewId, fieldName, groupFieldName))

  override def addBasicStats(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = dispatchIsAdminOrPermissionAndOwnerAjax(dataViewId, _.addBasicStats(dataViewId, fieldNames))

  override def addIndependenceTest(
    dataViewId: BSONObjectID,
    targetFieldName: String
  ) = dispatchIsAdminOrPermissionAndOwnerAjax(dataViewId, _.addIndependenceTest(dataViewId, targetFieldName))

  override def saveFilter(
    dataViewId: BSONObjectID,
    filterOrIds: Seq[Either[Seq[FilterCondition], BSONObjectID]]
  ) = dispatchIsAdminOrPermissionAndOwnerAjax(dataViewId, _.saveFilter(dataViewId, filterOrIds))

  // aux functions

  protected def dispatchIsAdminOrPermissionAndOwner(
    id: BSONObjectID,
    action: DataViewController => Action[AnyContent]
  ): Action[AnyContent] =
    dispatchIsAdminOrPermissionAndOwnerAux(dataViewOwner(id))(action)

  protected def dispatchIsAdminOrPermissionAndOwnerAjax(
    id: BSONObjectID,
    action: DataViewController => Action[AnyContent]
  ): Action[AnyContent] =
    dispatchIsAdminOrPermissionAndOwnerAux(dataViewOwner(id), unauthorizedDeadboltHandler)(action)

  private def dataViewOwner(id: BSONObjectID) = {
    request: Request[AnyContent] =>
      val dataSetId = getControllerId(request)
      val dsa = dsaf(dataSetId).getOrElse(throw new AdaException(s"Data set id $dataSetId not found."))
      dsa.dataViewRepo.get(id).map { dataView =>
        dataView.flatMap(_.createdById)
      }
  }
}