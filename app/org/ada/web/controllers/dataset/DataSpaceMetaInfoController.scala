package org.ada.web.controllers.dataset

import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import org.ada.server.models.DataSetFormattersAndIds._
import org.ada.server.models._
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.server.dataaccess.RepoTypes.{DataSetSettingRepo, DataSpaceMetaInfoRepo}
import org.incal.core.dataaccess.Criterion.Infix
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.data.Forms._
import play.api.mvc._
import reactivemongo.bson.BSONObjectID
import org.incal.play.security.SecurityUtil._
import views.html.{dataspace => view}
import play.api.data.Form

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import org.ada.server.AdaException
import org.ada.server.dataaccess.DataSetMetaInfoRepoFactory
import org.incal.play.controllers.{CrudControllerImpl, HasBasicFormCreateView, HasBasicListView, SubjectPresentRestrictedCrudController}
import org.ada.web.services.DataSpaceService
import org.incal.play.security.ActionSecurity.AuthActionTransformation

class DataSpaceMetaInfoController @Inject() (
    repo: DataSpaceMetaInfoRepo,
    dsaf: DataSetAccessorFactory,
    dataSetSettingRepo: DataSetSettingRepo,
    dataSpaceService: DataSpaceService,
    dataSetMetaInfoRepoFactory: DataSetMetaInfoRepoFactory
  ) extends AdaCrudControllerImpl[DataSpaceMetaInfo, BSONObjectID](repo)
    with SubjectPresentRestrictedCrudController[BSONObjectID]
    with HasBasicFormCreateView[DataSpaceMetaInfo]
    with HasBasicListView[DataSpaceMetaInfo] {

  override protected[controllers] val form = Form(
    mapping(
    "id" -> ignored(Option.empty[BSONObjectID]),
    "name" -> nonEmptyText,
    "sortOrder" -> number,
    "timeCreated" -> ignored(new java.util.Date()),
    "dataSetMetaInfos" -> ignored(Seq[DataSetMetaInfo]())
  ) (DataSpaceMetaInfo(_, _, _, _, _))(
      (item: DataSpaceMetaInfo) =>
        Some((item._id, item.name, item.sortOrder, item.timeCreated, item.dataSetMetaInfos))
  ))

  override protected val homeCall = routes.DataSpaceMetaInfoController.find()

  // create view

  override protected[controllers] def createView = { implicit ctx => view.create(_) }

  // show view

  override protected type ShowViewData = (
    DataSpaceMetaInfo,
    Int,
    Int,
    Boolean,
    Map[String, Int],
    Traversable[DataSpaceMetaInfo]
  )

  override protected def getFormShowViewData(
    id: BSONObjectID,
    form: Form[DataSpaceMetaInfo]
  ) = { implicit request =>
    for {
      // current user
      user <- currentUser()

      // get the edit-form data
      (_, newForm, subDataSpaceCount, subDataSetCount, dataSetSizes, tree) <- getFormEditViewData(id, form)(request)
    } yield {
      newForm.value.map( dataSpace =>
        (dataSpace, subDataSpaceCount, subDataSetCount, user.map(_.isAdmin).getOrElse(false), dataSetSizes, tree)
      ).getOrElse(
        throw new AdaException(s"Cannot access the data space '${id.stringify}'.")
      )
    }
  }

  override protected def showView = { implicit ctx =>
    (view.show(_, _, _, _, _, _)).tupled
  }

  // edit view and data

  override protected type EditViewData = (
    BSONObjectID,
    Form[DataSpaceMetaInfo],
    Int,
    Int,
    Map[String, Int],
    Traversable[DataSpaceMetaInfo]
  )

  override protected def getFormEditViewData(
    id: BSONObjectID,
    form: Form[DataSpaceMetaInfo]
  ) = { implicit request =>
    val treeFuture = dataSpaceService.getTreeForCurrentUser

    for {
      // get all the data spaces
      all <- treeFuture

      // find the data space in a tree
      dataSpace = all.map(dataSpaceService.findRecursively(id, _)).find(_.isDefined).flatten

      // calc the sizes of the children data sets
      dataSetSizes <- dataSpace match {
        case None => Future(Map[String, Int]())
        case Some(dataSpace) => getDataSetSizes(dataSpace)
      }
    } yield {
      val (subDataSpaceCount, subDataSetCount) = dataSpace.map { dataSpaceWithChildren =>
        val subDataSpaceCount = dataSpaceService.countDataSpacesNumRecursively(dataSpaceWithChildren)
        val subDataSetCount = dataSpaceService.countDataSetsNumRecursively(dataSpaceWithChildren)

        (subDataSpaceCount - 1, subDataSetCount)
      }.getOrElse((0, 0))

      (id, form.copy(value = dataSpace), subDataSpaceCount, subDataSetCount, dataSetSizes, all)
    }
  }

  override protected[controllers] def editView = { implicit ctx =>
    (view.edit(_, _, _, _, _, _)).tupled
  }

  override def edit(id: BSONObjectID) = restrictAdminAny(noCaching = true) (
    toAuthenticatedAction(
      super.edit(id)
    )
  )

  // list view

  override protected[controllers] def listView = { implicit ctx =>
    (view.list(_, _)).tupled
  }

  override protected def updateCall(
    item: DataSpaceMetaInfo)(
    implicit request: AuthenticatedRequest[AnyContent]
  ) =
    for {
      Some(existingItem) <- repo.get(item._id.get)
      // copy existing data set meta infos
      newDataSetMetaInfos = {
        val requestMap = request.body.asFormUrlEncoded.get
        val ids = requestMap.get("dataSetMetaInfos.id").get
        val newDataSetNames = requestMap.get("dataSetMetaInfos.name").get
        val newDataSetSortOrders = requestMap.get("dataSetMetaInfos.sortOrder").get
        val newHides = requestMap.get("dataSetMetaInfos.hide").get

        val existingDataSetMetaInfos = existingItem.dataSetMetaInfos
        val dataSetMetaInfoIdMap = existingDataSetMetaInfos.map( info => (info._id.get, info)).toMap

        ((ids, newDataSetNames, newDataSetSortOrders).zipped, newHides).zipped.map{ case ((id, newDataSetName, newDataSetSortOrder), newHide) =>
          val existingDataSetMetaInfo = dataSetMetaInfoIdMap(BSONObjectID.parse(id).get)

          val newSortOrder = try {
            newDataSetSortOrder.toInt
          } catch {
            // if it's not an int use an existing sort order
            case e: NumberFormatException => existingDataSetMetaInfo.sortOrder
          }

          existingDataSetMetaInfo.copy(name = newDataSetName, sortOrder = newSortOrder, hide = newHide.equals("true"))
        }
      }

      // update the data space meta info
      id <-
        repo.update(item.copy(
          dataSetMetaInfos = newDataSetMetaInfos.toSeq,
          timeCreated = existingItem.timeCreated,
          parentId = existingItem.parentId
        ))

      // update the individual data set meta infos
      _ <- Future.sequence(
          newDataSetMetaInfos.map( newDataSetMetaInfo =>
            dsaf(newDataSetMetaInfo.id).map { dsa =>
              dsa.updateMetaInfo(newDataSetMetaInfo)
            }.getOrElse(
              Future(())
            )
          )
        )
    } yield
      id

  // if update successful redirect to get/show instead of list
  override def update(id: BSONObjectID) = restrictAdminAny(noCaching = true) (
    toAuthenticatedAction(
      update(id, _ => Redirect(routes.DataSpaceMetaInfoController.get(id)))
    )
  )

  def deleteDataSet(id: BSONObjectID) = restrictAdminAny(noCaching = true) {
    implicit request =>
      implicit val msg = messagesApi.preferred(request)
      repo.get(id).flatMap(_.fold(
        Future(NotFound(s"Entity #$id not found"))
      ) { dataSpaceInfo =>
        val requestMap = request.body.asFormUrlEncoded.get
        val dataSetId = requestMap.get("dataSetId").get.head
        val actionChoice = requestMap.get("actionChoice").get.head

        val dsa = dsaf(dataSetId).get

        def unregisterDataSet: Future[_] =
          dataSpaceService.unregister(dataSpaceInfo, dataSetId)

        def deleteDataSet: Future[_] =
          for {
            _ <- dsa.updateDataSetRepo
            _ <- dsa.dataSetRepo.deleteAll
          } yield
            ()

        def deleteFields: Future[_] =
          dsa.fieldRepo.deleteAll

        def deleteCategories: Future[_] =
          dsa.categoryRepo.deleteAll

        def deleteViews: Future[_] =
          dsa.dataViewRepo.deleteAll

        def deleteFilters: Future[_] =
          dsa.filterRepo.deleteAll

        def deleteSetting: Future[_] =
          dsa.setting.flatMap ( setting =>
            dataSetSettingRepo.delete(setting._id.get)
          )

        val future = actionChoice match {

          case "1" =>
            unregisterDataSet

          case "2" => for {
            _ <- unregisterDataSet
            _ <- deleteDataSet
          } yield ()

          case "3" => for {
            _ <- unregisterDataSet
            _ <- deleteDataSet
            _ <- deleteFields
            _ <- deleteCategories
          } yield ()

          case "4" => for {
            _ <- unregisterDataSet
            _ <- deleteDataSet
            _ <- deleteFields
            _ <- deleteCategories
            _ <- deleteSetting
            _ <- deleteViews
            _ <- deleteFilters
          } yield ()
        }

        future.map(_ =>
          Redirect(routes.DataSpaceMetaInfoController.edit(id))
        )
      })
  }

  private def getDataSetSizesRecurrently(
    spaceMetaInfo: DataSpaceMetaInfo
  ): Future[Map[String, Int]] = {
    val singleMapfuture = getDataSetSizes(spaceMetaInfo)
    val recFutures = spaceMetaInfo.children.map(getDataSetSizes)

    for {
      simpleMap <- singleMapfuture
      mergeSubMap <-
        Future.sequence(recFutures).map { maps =>
        maps.foldLeft(Map[String, Int]()) { case (a, b) =>
          a ++ b
        }
      }
    } yield {
      simpleMap ++ mergeSubMap
    }
  }

  private def getDataSetSizes(
    spaceMetaInfo: DataSpaceMetaInfo
  ): Future[Map[String, Int]] = {
    val futures = spaceMetaInfo.dataSetMetaInfos.map { setMetaInfo =>
      val dsa = dsaf(setMetaInfo.id).get
      dsa.dataSetRepo.count().map(size => (setMetaInfo.id, size))
    }
    Future.sequence(futures).map(_.toMap)
  }

  //  // get is allowed for all logged users
//  override def get(id: BSONObjectID) = deadbolt.SubjectPresent()(super.get(id))
}