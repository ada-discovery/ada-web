package org.ada.web.controllers.dataset.datatrans

import java.util.Date

import javax.inject.Inject
import org.ada.server.dataaccess.RepoTypes.{DataSetTransformationRepo, DataSpaceMetaInfoRepo, MessageRepo}
import org.ada.server.models.DataSetFormattersAndIds.CategoryIdentity
import org.ada.server.models.DataSpaceMetaInfo
import org.ada.server.models.datatrans.DataSetTransformation.{DataSetTransformationExt, DataSetTransformationIdentity, dataSetTransformationFormat}
import org.ada.server.models.datatrans.DataSetTransformation
import org.ada.server.services.{DataSetService, LookupCentralExec, Scheduler, StaticLookupCentral}
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
    dataSetTransformationFormViewsCentral: StaticLookupCentral[DataSetTransformationFormViews[DataSetTransformation]],
    dataSpaceService: DataSpaceService,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    messageRepo: MessageRepo
  ) extends AdaCrudControllerImpl[DataSetTransformation, BSONObjectID](repo)
    with AdminRestrictedCrudController[BSONObjectID]
    with HasCreateEditSubTypeFormViews[DataSetTransformation, BSONObjectID]
    with HasFormShowEqualEditView[DataSetTransformation, BSONObjectID] {

  private val logger = Logger
  override protected val entityNameKey = "dataSetTransformation"
  override protected def formatId(id: BSONObjectID) = id.stringify

  private lazy val importRetryNum = configuration.getInt("datasetimport.retrynum").getOrElse(3)

  override protected val createEditFormViews = dataSetTransformationFormViewsCentral()
  private val transformationClassesAndNames = createEditFormViews.map(x => (x.man.runtimeClass, x.displayName))

  // default form... unused
  override protected val form = CopyFormViews.form.asInstanceOf[Form[DataSetTransformation]]

  override protected val homeCall = routes.DataSetTransformationController.find()

  // List views

  override protected type ListViewData = (
    Page[DataSetTransformation],
    Seq[FilterCondition],
    Traversable[(Class[_], String)],
    Traversable[DataSpaceMetaInfo]
  )

  override protected def getListViewData(
    page: Page[DataSetTransformation],
    conditions: Seq[FilterCondition]
  ) = { request =>
    for {
      tree <- dataSpaceService.getTreeForCurrentUser(request)
    } yield
      (page, conditions, transformationClassesAndNames, tree)
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
    importInfo: DataSetTransformation)(
    implicit request: Request[AnyContent]
  ) =
    super.saveCall(importInfo).map { id => scheduleOrCancel(id, importInfo); id }

  override protected def updateCall(
    importInfo: DataSetTransformation)(
    implicit request: Request[AnyContent]
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

  def dataSetNameAndLabels = Action.async { implicit request =>
    for {
      dataSpaces <- dataSpaceMetaInfoRepo.find()
    } yield {
      val dataSetNameLabels = dataSpaces.flatMap(_.dataSetMetaInfos).toSeq.sortBy(_.id).map { dataSetInfo =>
        Json.obj("name" -> dataSetInfo.id , "label" -> dataSetInfo.name)
      }
      Ok(Json.toJson(dataSetNameLabels))
    }
  }

  def copy(id: BSONObjectID) = restrictAny {
    implicit request =>
      repo.get(id).flatMap(_.fold(
        Future(NotFound(s"Data set transformation #${id.stringify} not found"))
      ) { dataSetTransformation =>
        val newDataSetTransformation = DataSetTransformationIdentity.clear(dataSetTransformation).copyWithTimestamps(new java.util.Date(), None)

        super.saveCall(newDataSetTransformation).map { newId =>
          scheduleOrCancel(newId, newDataSetTransformation)
          Redirect(routes.DataSetTransformationController.get(newId)).flashing("success" -> s"Data set transformation '${dataSetTransformation.sourceDataSetIds.mkString(", ")}' has been copied.")
        }
      }
    )
  }

  override protected def deleteCall(
    id: BSONObjectID)(
    implicit request: Request[AnyContent]
  ) =
    super.deleteCall(id).map { _ => dataSetTransformationScheduler.cancel(id); ()}

  private def scheduleOrCancel(
    id: BSONObjectID,
    importInfo: DataSetTransformation
  ): Unit = {
    if (importInfo.scheduled)
      dataSetTransformationScheduler.schedule(importInfo.scheduledTime.get)(id)
    else
      dataSetTransformationScheduler.cancel(id)
  }
}