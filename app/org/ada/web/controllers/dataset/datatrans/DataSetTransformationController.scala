package org.ada.web.controllers.dataset.datatrans

import java.util.Date

import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import org.ada.server.dataaccess.RepoTypes.{DataSetTransformationRepo, DataSpaceMetaInfoRepo, MessageRepo}
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.models.DataSpaceMetaInfo
import org.ada.server.models.datatrans.DataSetTransformation.{DataSetMetaTransformationIdentity, dataSetMetaTransformationFormat}
import org.ada.server.models.datatrans.{DataSetMetaTransformation, DataSetTransformation}
import org.ada.server.models.ScheduledTime.fillZeroes
import org.ada.server.services.{DataSetService, StaticLookupCentral}
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.web.services.DataSpaceService
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.AscSort
import org.incal.core.util.{nonAlphanumericToUnderscore, retry}
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
import scala.util.Random

class DataSetTransformationController @Inject()(
    repo: DataSetTransformationRepo,
    dataSetService: DataSetService,
    dataSetCentralTransformer: DataSetCentralTransformer,
    dataSetTransformationScheduler: DataSetTransformationScheduler,
    dataSetTransformationFormViewsCentral: StaticLookupCentral[DataSetMetaTransformationFormViews[DataSetMetaTransformation]],
    dataSpaceService: DataSpaceService,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    dsaf: DataSetAccessorFactory,
    messageRepo: MessageRepo
  ) extends AdaCrudControllerImpl[DataSetMetaTransformation, BSONObjectID](repo)
    with AdminRestrictedCrudController[BSONObjectID]
    with HasCreateEditSubTypeFormViews[DataSetMetaTransformation, BSONObjectID]
    with HasFormShowEqualEditView[DataSetMetaTransformation, BSONObjectID] {

  private val logger = Logger
  override protected val entityNameKey = "dataSetTransformation"
  override protected def formatId(id: BSONObjectID) = id.stringify
  private val random = new Random()

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
    transformation: DataSetMetaTransformation)(
    implicit request: AuthenticatedRequest[AnyContent]
  ) = {
    val transformationWithFixedScheduledTime = transformation.copyCore(
      transformation._id, transformation.timeCreated, transformation.timeLastExecuted, transformation.scheduled, transformation.scheduledTime.map(fillZeroes)
    )

    println(transformationWithFixedScheduledTime)

    super.saveCall(transformationWithFixedScheduledTime).map { id =>
      scheduleOrCancel(id, transformationWithFixedScheduledTime); id
    }
  }

  override protected def updateCall(
    transformation: DataSetMetaTransformation)(
    implicit request: AuthenticatedRequest[AnyContent]
  ) = {
    val transformationWithFixedScheduledTime = transformation.copyCore(
      transformation._id, transformation.timeCreated, transformation.timeLastExecuted, transformation.scheduled, transformation.scheduledTime.map(fillZeroes)
    )

    super.updateCall(transformationWithFixedScheduledTime).map { id =>
      scheduleOrCancel(id, transformationWithFixedScheduledTime); id
    }
  }

  def idAndNames = restrictAny {
    implicit request =>
      for {
        transformations <- repo.find(sort = Seq(AscSort("name")))
      } yield {
        val idAndNames = transformations.map { transformation =>
          val transformationName = transformation.sourceDataSetIds.mkString(", ") + " (" + transformationClassNameMap.get(transformation.getClass).get + ")"
          Json.obj(
            "_id" -> transformation._id,
            "name" -> transformationName
          )
        }
        Ok(JsArray(idAndNames.toSeq))
      }
  }

  def copy(id: BSONObjectID) = restrictAny {
    implicit request =>
      repo.get(id).flatMap(_.fold(
        Future(NotFound(s"Data set transformation #${id.stringify} not found"))
      ) { transformation =>

        val newDataSetTransformation = transformation.copyCore(
          None, new java.util.Date(), None, transformation.scheduled, transformation.scheduledTime
        )

        super.saveCall(newDataSetTransformation).map { newId =>
          scheduleOrCancel(newId, newDataSetTransformation)
          Redirect(routes.DataSetTransformationController.get(newId)).flashing("success" -> s"Data set transformation '${transformation.sourceDataSetIds.mkString(", ")}' has been copied.")
        }
      }
    )
  }

  override protected def deleteCall(
    id: BSONObjectID)(
    implicit request: AuthenticatedRequest[AnyContent]
  ) =
    super.deleteCall(id).map { _ => dataSetTransformationScheduler.cancel(id); ()}

  def resultDataSetIdAndName(
    sourceDataSetId: String,
    resultNameSuffix: String,
    transformationId: Option[BSONObjectID]
  ) = restrictAny { implicit request =>

    // aux function to get result data set id
    def resultDataSetId(transformation: DataSetMetaTransformation): Option[String] =
      transformation match {
        case x: DataSetTransformation => Some(x.resultDataSetId)
        case _ => None
      }

    for {
      dataSpaces <- dataSpaceMetaInfoRepo.find()

      transformations <- repo.find()

      currentDataSetId <- transformationId.map(id =>
          repo.get(id).map(_.flatMap(resultDataSetId))
        ).getOrElse(Future(None))

    } yield {
      val dataSetIdNames = dataSpaces.flatMap(_.dataSetMetaInfos.map(info => (info.id, info.name))).toSeq
      val resultDataSetIds = transformations.map(resultDataSetId)

      val sourceDataSetName = dataSetIdNames.find(_._1 == sourceDataSetId).map(_._2).getOrElse(sourceDataSetId)

      val allDataSetIds = dataSetIdNames.map(_._1) ++ resultDataSetIds

      val newDataSetId = sourceDataSetId + "_" + nonAlphanumericToUnderscore(resultNameSuffix.trim()).toLowerCase()
      val newDataSetName = sourceDataSetName + " " + resultNameSuffix

      val (newDataSetIdFixed, newDataSetNameFixed) =
        if (currentDataSetId.isDefined && currentDataSetId.get == newDataSetId) {
          // all is good we used it before
          (newDataSetId, newDataSetName)
        } else {
          if (allDataSetIds.exists(_ == newDataSetId)) {
            // already exists => need to add a random suffix
            val randomSuffix = (1 to 5).map { _ => random.nextInt(10).toString }.mkString
            (newDataSetId + "_" + randomSuffix, newDataSetName + " [" + randomSuffix + "]")
          } else
            (newDataSetId, newDataSetName)
        }

      Ok(
        Json.obj("id" -> newDataSetIdFixed , "name" -> newDataSetNameFixed)
      )
    }
  }

  def filterIdAndNames(
    dataSetId: String
  ) = restrictAny { implicit request =>
    dsaf(dataSetId).map { dsa =>
      for {
        filters <- dsa.filterRepo.find()
      } yield {
        val idAndNames = filters.toSeq.map(filter =>
          Json.obj("_id" -> filter._id, "name" -> filter.name)
        )
        Ok(JsArray(idAndNames))
      }
    }.getOrElse(
      Future(BadRequest(s"Data set '${dataSetId}' not found."))
    )
  }

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