package org.ada.web.controllers.dataset

import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import com.google.inject.assistedinject.Assisted
import org.ada.web.controllers.core.AdaExceptionHandler
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.web.models.D3Node
import org.ada.server.models._
import org.ada.server.models.DataSetFormattersAndIds._
import org.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Request, RequestHeader}
import play.api.routing.JavaScriptReverseRouter
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import org.incal.core.dataaccess.Criterion.Infix
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.AscSort
import org.incal.play.Page
import org.incal.play.controllers._
import org.incal.play.util.WebUtil.getRequestParamMap
import org.ada.web.services.DataSpaceService
import views.html.{category => view}

import scala.concurrent.Future

trait CategoryControllerFactory {
  def apply(dataSetId: String): CategoryController
}

protected[controllers] class CategoryControllerImpl @Inject() (
    @Assisted val dataSetId: String,
    dsaf: DataSetAccessorFactory,
    dataSpaceService: DataSpaceService
  ) extends AdaCrudControllerImpl[Category, BSONObjectID](dsaf(dataSetId).get.categoryRepo)
    with CategoryController
    with HasFormShowEqualEditView[Category, BSONObjectID] {

//  implicit val ec = ExecutionContexts.fixed1000ThreadEC

  protected val dsa: DataSetAccessor = dsaf(dataSetId).get

  private val logger = Logger  // (this.getClass())

  protected val fieldRepo = dsa.fieldRepo

  override protected val listViewColumns = Some(Seq(CategoryIdentity.name, "name", "label"))
  override protected val entityNameKey = "category"
  override protected def formatId(id: BSONObjectID) = id.stringify

  override protected[controllers] val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "name" -> nonEmptyText,
      "label" -> optional(nonEmptyText),
      "parentId" -> optional(nonEmptyText)
    ) { (id, name, label, parentId) =>
      Category(id, name, label, parentId.map(BSONObjectID.parse(_).get))
    }
    ((category: Category) => Some(category._id, category.name, category.label, category.parentId.map(_.stringify)))
  )

  private implicit def dataSetWebContext(implicit context: WebContext) = DataSetWebContext(dataSetId)

  protected val router = new CategoryRouter(dataSetId)
  protected val jsRouter = new CategoryJsRouter(dataSetId)
  protected val fieldRouter = new DictionaryRouter(dataSetId)

  override protected val homeCall = router.plainList

  // create view and data

  override protected type CreateViewData = (
    String,
    Form[Category],
    Traversable[Category]
  )

  override protected def getFormCreateViewData(form: Form[Category]) = {
    val dataSetNameFuture = dsa.dataSetName
    val categoriesFuture = allCategoriesFuture

    for {
      dataSetName <- dataSetNameFuture
      allCategories <- categoriesFuture
    } yield
      (dataSetName + " Category", form, allCategories)
  }

  override protected def createView = { implicit ctx =>
    (view.create(_, _, _)).tupled
  }

  // edit view and data (= show view)

  override protected type EditViewData = (
    String,
    BSONObjectID,
    Form[Category],
    Traversable[Category],
    Traversable[Field],
    DataSetSetting,
    Traversable[DataSpaceMetaInfo]
  )

  override protected def getFormEditViewData(
    id: BSONObjectID,
    form: Form[Category]
  ) = { request =>
    val assocFieldsFuture = fieldRepo.find(
      criteria = Seq("categoryId" #== Some(id)),
      sort = Seq(AscSort("name"))
    )
    val dataSetNameFuture = dsa.dataSetName
    val treeFuture = dataSpaceService.getTreeForCurrentUser(request)
    val categoriesFuture = allCategoriesFuture
    val dataSetSettingFuture = dsa.setting

    for {
      // retrieve the associated fields
      fields <- assocFieldsFuture

      // get the data set name
      dataSetName <- dataSetNameFuture

      // get the data space tree
      tree <- treeFuture

      // retrieve all the categories
      allCategories <- categoriesFuture

      // get the setting
      setting <- dataSetSettingFuture
    } yield
      (dataSetName + " Category", id, form, allCategories, fields, setting, tree)
  }

  override protected def editView = { implicit ctx =>
    (view.edit(_, _, _, _, _, _, _)).tupled
  }

  // list view and data

  override protected type ListViewData = (
    String,
    Page[Category],
    Seq[FilterCondition],
    DataSetSetting,
    Traversable[DataSpaceMetaInfo]
  )

  override protected def getListViewData(
    page: Page[Category],
    conditions: Seq[FilterCondition]
  ) = { request =>
    val treeFuture = dataSpaceService.getTreeForCurrentUser(request)
    val nameFuture = dsa.dataSetName
    val dataSetSettingFuture = dsa.setting

    for {
      tree <- treeFuture
      dataSetName <- nameFuture
      setting <- dataSetSettingFuture
    } yield
      (dataSetName + " Category", page, conditions, setting, tree)
  }

  override protected def listView = { implicit ctx => (view.list(_, _, _, _, _)).tupled }

  override protected def deleteCall(
    id: BSONObjectID)(
    implicit request: AuthenticatedRequest[AnyContent]
  ) = {
    // relocate the children to a new parent
    val updateChildrenFutures =
      for {
        Some(category) <- repo.get(id)
        children <- repo.find(Seq("parentId" #== Some(id)))
      } yield
        children.map{ child =>
          child.parentId = category.parentId
          repo.update(child)
        }

    // remove the field category refs
    val updateFieldFutures =
      for {
        fields <- fieldRepo.find(Seq("categoryId" #== Some(id)))
      } yield
        fields.map { field =>
          field.categoryId = None
          fieldRepo.update(field)
        }

    // finally, combine all the futures and delete the category
    for {
      updateFutures1 <- updateChildrenFutures
      updateFutures2 <- updateFieldFutures
      _ <- Future.sequence(updateFutures1)
      _ <- Future.sequence(updateFutures2)
    } yield
      repo.delete(id)
  }

  override protected def updateCall(
    category: Category)(
    implicit request: AuthenticatedRequest[AnyContent]
  ) =
    for {
      // collect the fields previously associated with a category
      oldFields <- fieldRepo.find(Seq("categoryId" #== category._id))

      // disassociate the old fields
      _ <- {
        val disassociatedFields = oldFields.map(_.copy(categoryId = None))
        fieldRepo.update(disassociatedFields).map(_ => Some(()))
      }

      // colect the newly associated fileds
      newFields <- {
        val fieldNames = getRequestParamMap(request).get("fields[]").getOrElse(Nil)
        fieldRepo.find(Seq(FieldIdentity.name #-> fieldNames))
      }

      // update them
      _ <- {
        val associatedFields = newFields.map(_.copy(categoryId = category._id))
        fieldRepo.update(associatedFields).map(_ => Some(()))
      }

      // update the category itself
      id <- repo.update(category)
    } yield
      id

  override def getCategoryD3Root = Action.async { implicit request =>
    allCategoriesFuture.map { categories =>
      val idD3NodeMap = categories.map(category => (category._id.get, D3Node(category._id, category.name, None))).toMap

      categories.foreach { category =>
        if (category.parentId.isDefined) {
          val node = idD3NodeMap(category._id.get)
          val parent = idD3NodeMap(category.parentId.get)
          parent.children = parent.children ++ Seq(node)
        }
      }

      val layerOneCategories = categories.filter(_.parentId.isEmpty)

      val root = D3Node(None, "Root", None, layerOneCategories.map(category => idD3NodeMap(category._id.get)).toSeq)

      Ok(Json.toJson(root))
    }
  }

  override def relocateToParent(id: BSONObjectID, parentId: Option[BSONObjectID]) = Action.async{ implicit request =>
    repo.get(id).flatMap{ category =>
      if (category.isEmpty)
        Future(notFoundCategory(id))
      else if (parentId.isDefined)
        repo.get(parentId.get).flatMap { parent =>
          if (parent.isEmpty)
            Future(notFoundCategory(parentId.get))
          else {
            category.get.parentId = parent.get._id
            repo.update(category.get).map(_ => Ok("Done"))
          }
        }
      else {
        category.get.parentId = None
        repo.update(category.get).map(_ => Ok("Done"))
      }
    }
  }

  override def saveForName(name: String) = Action.async{ implicit request =>
    repo.save(Category(None, name)).map( id => Ok(Json.toJson(id)))
  }

  override def addFields(
    categoryId: BSONObjectID,
    fieldNames: Seq[String]
  ) = Action.async { implicit request =>
    for {
      category <- repo.get(categoryId)
      fields <- fieldRepo.find(Seq(FieldIdentity.name #-> fieldNames))
      response <- category match {
        case Some(category) => {
          val newFields = fields.map(_.copy(categoryId = Some(categoryId)))
          fieldRepo.update(newFields).map(_ => Some(()))
        }
        case None => Future(None)
      }
    } yield
      response.fold(
        NotFound(s"Category '#${categoryId.stringify}' not found")
      ) { _ => Ok("Done")}
  }

  override def idAndNames = Action.async { implicit request =>
    for {
      categories <- repo.find(
        sort = Seq(AscSort("name")),
        projection = Seq(CategoryIdentity.name, "name")
      )
    } yield {
      Ok(Json.toJson(categories))
    }
  }

  private def notFoundCategory(id: BSONObjectID) =
    NotFound(s"Category with id #${id.stringify} not found. It has been probably deleted (by a different user). It's highly recommended to refresh your screen.")

  override def updateLabel(id: BSONObjectID, label: String) = Action.async { implicit request =>
    repo.get(id).flatMap(_.fold(
      Future(NotFound(s"Category '$id' not found"))
    ){ category =>
      repo.update(category.copy(label = Some(label))).map(_ =>
        Ok("Done")
      )
    })
  }

  protected def allCategoriesFuture =
    repo.find(sort = Seq(AscSort("name")))
}