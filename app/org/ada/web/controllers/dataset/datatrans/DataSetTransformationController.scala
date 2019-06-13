package org.ada.web.controllers.dataset.datatrans

import java.util.Date

import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import org.ada.server.dataaccess.RepoTypes.{DataSetTransformationRepo, DataSpaceMetaInfoRepo, MessageRepo}
import org.ada.server.models.DataSpaceMetaInfo
import org.ada.server.models.datatrans.DataSetTransformation.{DataSetMetaTransformationExt, DataSetMetaTransformationIdentity, dataSetMetaTransformationFormat}
import org.ada.server.models.datatrans.{DataSetMetaTransformation, DataSetTransformation}
import org.ada.server.services.{DataSetService, StaticLookupCentral}
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.web.services.DataSpaceService
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.AscSort
import org.incal.core.util.retry
import org.incal.play.Page
import org.incal.play.controllers._
import org.ada.server.services.ServiceTypes._
import play.api.Logger
import play.api.data.Form
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsArray, Json}
import play.api.mvc._
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import views.html.{datasettrans => view}

import scala.concurrent.Future

class DataSetTransformationController @Inject()(
    repo: DataSetTransformationRepo,
    dataSetService: DataSetService,
    dataSetCentralTransformer: DataSetCentralTransformer,
    dataSetTransformationScheduler: DataSetTransformationScheduler,
    dataSetTransformationFormViewsCentral: StaticLookupCentral[DataSetMetaTransformationFormViews[DataSetMetaTransformation]],
    dataSpaceService: DataSpaceService,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    messageRepo: MessageRepo
  ) extends AdaCrudControllerImpl[DataSetMetaTransformation, BSONObjectID](repo)
    with AdminRestrictedCrudController[BSONObjectID]
    with HasCreateEditSubTypeFormViews[DataSetMetaTransformation, BSONObjectID]
    with HasFormShowEqualEditView[DataSetMetaTransformation, BSONObjectID] {

  private val logger = Logger
  override protected val entityNameKey = "dataSetTransformation"
  override protected def formatId(id: BSONObjectID) = id.stringify

  private lazy val importRetryNum = configuration.getInt("datasetimport.retrynum").getOrElse(3)

  override protected val createEditFormViews = dataSetTransformationFormViewsCentral()
  private val transformationClassNameMap: Map[Class[_], String] = createEditFormViews.map(x => (x.man.runtimeClass, x.displayName)).toMap

  // default form... unused
  override protected val form = CopyFormViews.form.asInstanceOf[Form[DataSetMetaTransformation]]

  override protected val homeCall = routes.DataSetTransformationController.find()

  // List views

  override protected type ListViewData = (
    Page[DataSetMetaTransformation],
    Seq[FilterCondition],
    Map[Class[_], String],
    Traversable[DataSpaceMetaInfo]
  )

  override protected def getListViewData(
    page: Page[DataSetMetaTransformation],
    conditions: Seq[FilterCondition]
  ) = { implicit request =>
    for {
      tree <- dataSpaceService.getTreeForCurrentUser
    } yield
      (page, conditions, transformationClassNameMap, tree)
  }

  override protected def listView = { implicit ctx => (view.list(_, _, _, _)).tupled }

  // rest

  override def create(concreteClassName: String) = restrictAny(super.create(concreteClassName))

  def execute(id: BSONObjectID) = restrictAny {
    implicit request =>
      repo.get(id).flatMap(_.fold(
        Future(NotFound(s"Data set transformation #${id.stringify} not found"))
      ) { transformationInfo =>
          val start = new Date()

          val sourceIdsString = transformationInfo.sourceDataSetIds.mkString(", ")
          retry(s"Data set(s) '$sourceIdsString' transformation failed: ", logger.warn(_), importRetryNum)(
            dataSetCentralTransformer(transformationInfo)
          ).map { _ =>
            val execTimeSec = (new Date().getTime - start.getTime) / 1000

            render {
              case Accepts.Html() => referrerOrHome().flashing("success" -> s"Data set(s) '$sourceIdsString' has been transformed in $execTimeSec sec(s).")
              case Accepts.Json() => Created(Json.obj("message" -> s"Data set has been transformed in $execTimeSec sec(s)", "name" -> transformationInfo.sourceDataSetIds))
            }
          }.recover(handleExceptions("execute"))
        }
      )
  }

  override protected def saveCall(
    importInfo: DataSetMetaTransformation)(
    implicit request: AuthenticatedRequest[AnyContent]
  ) =
    super.saveCall(importInfo).map { id => scheduleOrCancel(id, importInfo); id }

  override protected def updateCall(
    importInfo: DataSetMetaTransformation)(
    implicit request: AuthenticatedRequest[AnyContent]
  ) =
    super.updateCall(importInfo).map { id => scheduleOrCancel(id, importInfo); id }

  def idAndNames = restrictAny {
    implicit request =>
      for {
        transformations <- repo.find(sort = Seq(AscSort("name")))
      } yield {
        val idAndNames = transformations.map(transformation =>
          Json.obj(
            "_id" -> transformation._id,
            "name" -> transformation.sourceDataSetIds.mkString(", ")
          )
        )
        Ok(JsArray(idAndNames.toSeq))
      }
  }

  def dataSetIds = Action.async { implicit request =>
    for {
      dataSpaces <- dataSpaceMetaInfoRepo.find()
    } yield {
      val dataSetNameLabels = dataSpaces.flatMap(_.dataSetMetaInfos).toSeq.sortBy(_.id).map { dataSetInfo =>
        Json.obj("name" -> dataSetInfo.id , "label" -> dataSetInfo.id)
      }
      Ok(Json.toJson(dataSetNameLabels))
    }
  }

  def copy(id: BSONObjectID) = restrictAny {
    implicit request =>
      repo.get(id).flatMap(_.fold(
        Future(NotFound(s"Data set transformation #${id.stringify} not found"))
      ) { dataSetTransformation =>
        val newDataSetTransformation = DataSetMetaTransformationIdentity.clear(dataSetTransformation).copyWithTimestamps(new java.util.Date(), None)

        super.saveCall(newDataSetTransformation).map { newId =>
          scheduleOrCancel(newId, newDataSetTransformation)
          Redirect(routes.DataSetTransformationController.get(newId)).flashing("success" -> s"Data set transformation '${dataSetTransformation.sourceDataSetIds.mkString(", ")}' has been copied.")
        }
      }
    )
  }

  override protected def deleteCall(
    id: BSONObjectID)(
    implicit request: AuthenticatedRequest[AnyContent]
  ) =
    super.deleteCall(id).map { _ => dataSetTransformationScheduler.cancel(id); ()}

  private def scheduleOrCancel(
    id: BSONObjectID,
    transformationInfo: DataSetMetaTransformation
  ): Unit = {
    if (transformationInfo.scheduled)
      dataSetTransformationScheduler.schedule(transformationInfo.scheduledTime.get)(id)
    else
      dataSetTransformationScheduler.cancel(id)
  }
}