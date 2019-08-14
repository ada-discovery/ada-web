package org.ada.web.controllers.dataset

import java.util.concurrent.TimeoutException
import java.{util => ju}

import javax.inject.Inject
import org.ada.server.field.FieldUtil.FieldOps
import com.google.inject.assistedinject.Assisted
import org.ada.server.dataaccess.RepoTypes.UserRepo
import org.ada.server.dataaccess.dataset.DataViewRepo
import org.ada.server.models._
import org.ada.server.models.DataSetFormattersAndIds._
import org.ada.server.json.EitherFormat
import org.ada.server.models.Filter.{FilterIdentity, filterConditionFormat, filterFormat}
import org.incal.core.dataaccess.Criterion._
import org.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.{JsArray, Json}
import play.api.mvc.{Action, AnyContent, Request}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import java.util.Date

import be.objectify.deadbolt.scala.AuthenticatedRequest
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.server.AdaException
import org.ada.server.models.{DataView, WidgetGenerationMethod, WidgetSpec}
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.Criterion
import org.incal.play.Page
import org.incal.play.controllers.{CrudControllerImpl, HasFormShowEqualEditView, WebContext}
import org.incal.play.formatters._
import org.incal.play.security.AuthAction
import org.ada.web.models.security.DeadboltUser
import org.ada.web.services.DataSpaceService
import views.html.{dataview => view}

import scala.concurrent.Future
import scala.reflect.ClassTag

trait DataViewControllerFactory {
  def apply(dataSetId: String): DataViewController
}

protected[controllers] class DataViewControllerImpl @Inject() (
    @Assisted val dataSetId: String,
    dsaf: DataSetAccessorFactory,
    dataSpaceService: DataSpaceService,
    userRepo: UserRepo
  ) extends AdaCrudControllerImpl[DataView, BSONObjectID](dsaf(dataSetId).get.dataViewRepo)
    with DataViewController
    with HasFormShowEqualEditView[DataView, BSONObjectID] {

  protected val dsa: DataSetAccessor = dsaf(dataSetId).get

  protected lazy val dataViewRepo = dsa.dataViewRepo
  protected lazy val fieldRepo = dsa.fieldRepo

  override protected val listViewColumns = None // Some(Seq("name"))
  override protected val entityNameKey = "dataView"
  override protected def formatId(id: BSONObjectID) = id.stringify

  private implicit val widgetSpecFormatter = JsonFormatter[WidgetSpec]
  private implicit val eitherFormat = EitherFormat[Seq[FilterCondition], BSONObjectID]
  private implicit val eitherFormatter = JsonFormatter[Either[Seq[FilterCondition], BSONObjectID]]
  private implicit val widgetGenerationMethodFormatter = EnumFormatter(WidgetGenerationMethod)

  override protected[controllers] val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "name" -> nonEmptyText,
      "filterOrIds" -> seq(of[Either[Seq[FilterCondition], BSONObjectID]]),
      "tableColumnNames" -> seq(text),
      "widgetSpecs" -> seq(of[WidgetSpec]),
      "elementGridWidth" -> default(number(min = 1, max = 12), 3),
      "default" -> boolean,
      "isPrivate" -> boolean,
      "generationMethod" -> of[WidgetGenerationMethod.Value]
    ) {
       DataView(_, _, _, _, _, _, _, _, _)
     }
    ((item: DataView) => Some((item._id, item.name, item.filterOrIds, item.tableColumnNames, item.widgetSpecs, item.elementGridWidth, item.default, item.isPrivate, item.generationMethod)))
  )

  // router for requests; to be passed to views as helper.
  protected val router = new DataViewRouter(dataSetId)
  protected val jsRouter = new DataViewJsRouter(dataSetId)
  protected val dataSetRouter = new DataSetRouter(dataSetId)

  private implicit def dataSetWebContext(implicit context: WebContext) = DataSetWebContext(dataSetId)

  override protected val homeCall = router.plainList

  // create view and data

  override protected type CreateViewData = (
    String,
    Form[DataView],
    Option[FilterShowFieldStyle.Value]
  )

  override protected def getFormCreateViewData(form: Form[DataView]) =
    for {
      dataSetName <- dsa.dataSetName
      setting <- dsa.setting
    } yield
      (dataSetName + " Data View", form, setting.filterShowFieldStyle)

  override protected def createView = { implicit ctx =>
    (view.create(_, _, _)).tupled
  }

  // edit view and data (= show view)

  override protected type EditViewData = (
    String,
    BSONObjectID,
    Form[DataView],
    Map[String, Field],
    Map[BSONObjectID, String],
    Traversable[DataSpaceMetaInfo],
    DataSetSetting
  )

  override protected def getFormEditViewData(
    id: BSONObjectID,
    form: Form[DataView]
  ) = { request =>
    val dataSetNameFuture = dsa.dataSetName
    val nameFieldMapFuture = getNameFieldMap
    val settingFuture = dsa.setting

    val treeFuture = dataSpaceService.getTreeForCurrentUser(request)

    val setCreatedByFuture =
      form.value match {
        case Some(dataView) => DataViewRepo.setCreatedBy(userRepo, Seq(dataView))
        case None => Future(())
      }

    val filtersFuture =
      form.value match {
        case Some(dataView) =>
          val filterIds = dataView.widgetSpecs.map(_.subFilterId).flatten
          if (filterIds.nonEmpty) {
            // TODO: IN criterion with BSONObjectID does not work here
            dsa.filterRepo.find(
//              criteria = Seq(FilterIdentity.name #-> filterIds),
              projection = Seq("name")
            )
          } else
            Future(Nil)
        case None => Future(Nil)
      }

    for {
      dataSetName <- dataSetNameFuture
      setting <- settingFuture
      nameFieldMap <- nameFieldMapFuture
      tree <- treeFuture
      filters <- filtersFuture
      _ <- setCreatedByFuture
    } yield {
      val idFilterNameMap = filters.map( filter => (filter._id.get, filter.name.getOrElse(""))).toMap
      (dataSetName + " Data View", id, form, nameFieldMap, idFilterNameMap, tree, setting)
    }
  }

  override protected def editView = { implicit ctx =>
    (view.editNormal(_, _, _, _, _, _, _)).tupled
  }

  // list view and data

  override protected type ListViewData = (
    String,
    Page[DataView],
    Seq[FilterCondition],
    DataSetSetting,
    Traversable[DataSpaceMetaInfo]
  )

  override protected def getListViewData(
    page: Page[DataView],
    conditions: Seq[FilterCondition]
  ) = { request =>
    val setCreatedByFuture = DataViewRepo.setCreatedBy(userRepo, page.items)
    val dataSpaceTreeFuture = dataSpaceService.getTreeForCurrentUser(request)
    val dataSetNameFuture = dsa.dataSetName
    val settingFuture = dsa.setting

    for {
      _ <- setCreatedByFuture
      tree <- dataSpaceTreeFuture
      dataSetName <- dataSetNameFuture
      setting <- settingFuture
    } yield
      (dataSetName + " Data View", page, conditions, setting, tree)
  }

  override protected def listView = { implicit ctx =>
    (view.list(_, _, _, _, _)).tupled
  }

  // actions

  override def saveCall(
    dataView: DataView)(
    implicit request: AuthenticatedRequest[AnyContent]
  ): Future[BSONObjectID] =
    for {
      user <- currentUser()

      id <- {
        val dataViewWithUser = user match {
          case Some(user) =>
            val filteredDataView = removeCustomWidgetSpecsForNonAdmins(dataView, user.isAdmin)
            filteredDataView.copy(timeCreated = new Date(), createdById = user.id)

          case None => throw new AdaException("No logged user found")
        }
        repo.save(dataViewWithUser)
      }
    } yield
      id

  override protected def updateCall(
    dataView: DataView)(
    implicit request: AuthenticatedRequest[AnyContent]
  ): Future[BSONObjectID] =
    for {
      existingDataViewOption <- repo.get(dataView._id.get)

      user <- currentUser()

      id <- {
        user match {
          case Some(user) =>
            val mergedDataView = existingDataViewOption.fold(dataView) { existingDataView =>
              val filteredDataView = removeCustomWidgetSpecsForNonAdmins(dataView, user.isAdmin)
              filteredDataView.copy(createdById = existingDataView.createdById, timeCreated = existingDataView.timeCreated)
            }
            repo.update(mergedDataView)

          case None => throw new AdaException("No logged user found")
        }
      }
    } yield
      id

  // if non-admin we remove Custom HTML widgets, which are security "exploitable"
  private def removeCustomWidgetSpecsForNonAdmins(
    dataView: DataView,
    isAdmin: Boolean
  ) =
    if (!isAdmin)
      dataView.copy(widgetSpecs = dataView.widgetSpecs.filterNot(_.isInstanceOf[CustomHtmlWidgetSpec]))
    else
      dataView

  override def idAndNames = Action.async { implicit request =>
    for {
      dataViews <- repo.find(
//        sort = Seq(AscSort("name")),
        projection = Seq("name", "default", "elementGridWidth", "timeCreated", "generationMethod")
      )
    } yield {
      val sorted = dataViews.toSeq.sortBy(dataView =>
        (!dataView.default, dataView.name)
      )
      val idAndNames = sorted.map( dataView =>
        Json.obj(
          "_id" -> dataView._id,
          "name" -> dataView.name,
          "default" -> dataView.default
        )
      )
      Ok(JsArray(idAndNames))
    }
  }

  override def idAndNamesAccessible = AuthAction { implicit request =>
    // auxiliary function to find data views for given criteria
    def findAux(criteria: Seq[Criterion[Any]]) = repo.find(
      criteria = criteria,
      projection = Seq("name", "default", "elementGridWidth", "timeCreated", "isPrivate", "createdById", "generationMethod")
    )

    for {
      user <- currentUser()

      dataViews <-
        user match {
          case None => Future(Nil)

          case Some(user) =>
            // admin     => get all; non-admin => not private or owner
            if (user.isAdmin)
              findAux(Nil)
            else
              findAux(Nil).map { views =>
                views.filter { view =>
                  !view.isPrivate || (view.createdById.isDefined && view.createdById.equals(user.id))
                }
              }
              // TODO: fix Apache ignite to support boolean conditions

//              findAux(Seq("isPrivate" #== falsefid)).flatMap { nonPrivateViews =>
//                findAux(Seq("createdById" #== user._id)).map { ownedViews =>
//                  (nonPrivateViews ++ ownedViews).toSet
//                }
//              }
        }
    } yield {
      val sorted = dataViews.toSeq.sortBy(dataView =>
        (!dataView.default, dataView.name)
      )
      val idAndNames = sorted.map( dataView =>
        Json.obj(
          "_id" -> dataView._id,
          "name" -> dataView.name,
          "default" -> dataView.default
        )
      )
      Ok(JsArray(idAndNames))
    }
  }

  override def getAndShowView(id: BSONObjectID) =
    AuthAction { implicit request =>
      repo.get(id).flatMap(_.fold(
        Future(NotFound(s"Entity #$id not found"))
      ) { entity =>
          getEditViewData(id, entity)(request).map(viewData =>
            render {
              case Accepts.Html() => Ok(
                view.edit(
                  viewData._1,
                  viewData._2,
                  viewData._3,
                  viewData._4,
                  viewData._5,
                  viewData._6,
                  viewData._7,
                  router.updateAndShowView
                )
              )
              case Accepts.Json() => BadRequest("Edit function doesn't support JSON response. Use get instead.")
            }
          )
      }).recover {
        case t: TimeoutException =>
          Logger.error("Problem found in the edit process")
          InternalServerError(t.getMessage)
      }
    }

  override def updateAndShowView(id: BSONObjectID) =
    Action.async { implicit request =>
      update(
        id,
        {_ => Redirect(dataSetRouter.getView(id, Nil, Nil, false))}
      ).apply(request)
    }

  override def copy(id: BSONObjectID) =
    AuthAction { implicit request =>
      for {
        // get the data view
        dataView <- repo.get(id)

        // copy and save the new view
        newId <- dataView.fold(
          Future(Option.empty[BSONObjectID])
        ) { dataView =>
          val newDataView = dataView.copy(_id = None, name = dataView.name + " copy", default = false, timeCreated = new ju.Date)
          saveCall(newDataView).map(Some(_))
        }
      } yield {
        newId.fold(
          NotFound(s"Entity #$id not found")
        ) { newId =>
          Redirect(router.get(newId)).flashing("success" -> s"Data view '${dataView.get.name}' has been copied.")
        }
      }
  }

  override def addDistributions(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = processDataView(dataViewId)(
    addWidgetsAndUpdateView(fieldNames.map(DistributionWidgetSpec(_, None)))
  )

  override def addDistribution(
    dataViewId: BSONObjectID,
    fieldName: String,
    groupFieldName: Option[String]
  ) = processDataView(dataViewId)(
    addWidgetsAndUpdateView(Seq(DistributionWidgetSpec(fieldName, groupFieldName)))
  )

  override def addCumulativeCounts(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = processDataView(dataViewId)(
    addWidgetsAndUpdateView(fieldNames.map(CumulativeCountWidgetSpec(_, None)))
  )

  override def addCumulativeCount(
    dataViewId: BSONObjectID,
    fieldName: String,
    groupFieldName: Option[String]
  ) = processDataView(dataViewId)(
    addWidgetsAndUpdateView(Seq(CumulativeCountWidgetSpec(fieldName, groupFieldName)))
  )

  override def addBoxPlots(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = processDataView(dataViewId)(
    addWidgetsAndUpdateView(fieldNames.map(BoxWidgetSpec(_, None)))
  )

  override def addBoxPlot(
    dataViewId: BSONObjectID,
    fieldName: String,
    groupFieldName: Option[String]
  ) = processDataView(dataViewId)(
    addWidgetsAndUpdateView(Seq(BoxWidgetSpec(fieldName, groupFieldName)))
  )

  override def addBasicStats(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = processDataView(dataViewId)(
    addWidgetsAndUpdateView(fieldNames.map(BasicStatsWidgetSpec(_)))
  )

  override def addScatter(
    dataViewId: BSONObjectID,
    xFieldName: String,
    yFieldName: String,
    groupOrValueFieldName: Option[String]
  ) = processDataView(dataViewId) { dataView =>
    for {
      groupOrValueField <- groupOrValueFieldName.map(fieldRepo.get).getOrElse(Future(None))

      _ <- {
        val widgetSpec = if (groupOrValueField.map(_.isNumeric).getOrElse(false))
          ValueScatterWidgetSpec(xFieldName, yFieldName, groupOrValueFieldName.get)
        else
          ScatterWidgetSpec(xFieldName, yFieldName, groupOrValueFieldName)

        addWidgetsAndUpdateView(Seq(widgetSpec))(dataView)
      }
    } yield
      ()
  }

  override def addCorrelation(
    dataViewId: BSONObjectID,
    correlationType: CorrelationType.Value
  ) = Action.async { implicit request =>
    val fieldNames = request.body.asFormUrlEncoded.flatMap(_.get("fieldNames[]")).getOrElse(Nil)

    if (fieldNames.isEmpty)
      Future(BadRequest("No field names provided for addCorrelation function."))
    else
      processDataViewAux(dataViewId)(
        addWidgetsAndUpdateView(Seq(CorrelationWidgetSpec(fieldNames, correlationType)))
      )
  }

  override def addHeatmap(
    dataViewId: BSONObjectID,
    xFieldName: String,
    yFieldName: String,
    valueFieldName: String,
    aggType: AggType.Value
  ) = processDataView(dataViewId)(
    addWidgetsAndUpdateView(Seq(HeatmapAggWidgetSpec(xFieldName, yFieldName, valueFieldName, 20, 20, aggType)))
  )

  override def addGridDistribution(
    dataViewId: BSONObjectID,
    xFieldName: String,
    yFieldName: String
  ) = processDataView(dataViewId)(
    addWidgetsAndUpdateView(Seq(GridDistributionCountWidgetSpec(xFieldName, yFieldName, 20, 20)))
  )

  override def addIndependenceTest(
    dataViewId: BSONObjectID,
    targetFieldName: String
  ) = Action.async { implicit request =>

    val inputFieldNames = request.body.asFormUrlEncoded.flatMap(_.get("inputFieldNames[]")).getOrElse(Nil)

    if (inputFieldNames.isEmpty)
      Future(BadRequest("No input field names provided for addIndependenceTest function."))
    else
      processDataViewAux(dataViewId)(
        addWidgetsAndUpdateView(Seq(IndependenceTestWidgetSpec(targetFieldName, inputFieldNames)))
      )
  }

  override def addTableFields(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = processDataView(dataViewId) { dataView =>
    val existingFieldNames = dataView.tableColumnNames
    val filteredFieldNames = fieldNames.filter(!existingFieldNames.contains(_))
    if (filteredFieldNames.nonEmpty) {
      val newDataView = dataView.copy(tableColumnNames = existingFieldNames ++ filteredFieldNames)
      repo.update(newDataView)
    } else {
      Future(())
    }
  }

  private def filterSpecsOf[T <: WidgetSpec](
    dataView: DataView)(
    implicit ev: ClassTag[T]
  ): Seq[T] =
    dataView.widgetSpecs.collect{ case t: T => t}

  private def addWidgetsAndUpdateView(
    widgetSpecs: Seq[WidgetSpec])(
    dataView: DataView
  ) = {
    val newDataView = dataView.copy(widgetSpecs = dataView.widgetSpecs ++ widgetSpecs)
    repo.update(newDataView)
  }

  protected def processDataView(id: BSONObjectID)(fun: DataView => Future[_]) =
    Action.async { implicit request =>
      processDataViewAux(id)(fun)
    }

  protected def processDataViewAux(
    id: BSONObjectID)(
    fun: DataView => Future[_])(
    implicit request: Request[_]
  ) =
    for {
      dataView <- repo.get(id)
      response <- dataView match {
        case Some(dataView) => fun(dataView).map(x => Some(x))
        case None => Future(None)
      }
    } yield
      response.fold(
        NotFound(s"Data view '#${id.stringify}' not found")
      ) { _ => Ok("Done")}

  override def saveFilter(
    dataViewId: BSONObjectID,
    filterOrIds: Seq[Either[Seq[FilterCondition], BSONObjectID]]
  ) = processDataView(dataViewId) { dataView =>
    val newDataView = dataView.copy(filterOrIds = filterOrIds)
    repo.update(newDataView)
  }

  private def getNameFieldMap: Future[Map[String, Field]] =
    fieldRepo.find().map { _.map( field =>
        (field.name, field)
      ).toMap
    }
}