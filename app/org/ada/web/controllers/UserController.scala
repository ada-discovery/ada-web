package org.ada.web.controllers

import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.web.controllers.dataset._
import org.ada.server.dataaccess.RepoTypes.{DataSpaceMetaInfoRepo, UserRepo}
import play.api.data.Form
import play.api.data.Forms.{email, ignored, mapping, boolean, nonEmptyText, seq, text}
import org.ada.server.models.{DataSpaceMetaInfo, User}
import org.incal.core.dataaccess.AscSort
import reactivemongo.bson.BSONObjectID
import views.html.{user => view}
import play.api.mvc.{Action, AnyContent, Request, RequestHeader}
import reactivemongo.play.json.BSONFormats._
import org.incal.core.util.ReflectionUtil.getMethodNames
import org.incal.play.Page
import org.incal.play.controllers.{AdminRestrictedCrudController, CrudControllerImpl, HasBasicListView, HasFormShowEqualEditView}
import play.api.libs.json.{JsArray, Json}
import play.api.libs.mailer.{Email, MailerClient}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserController @Inject() (
    userRepo: UserRepo,
    mailerClient: MailerClient,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo
  ) extends AdaCrudControllerImpl[User, BSONObjectID](userRepo)
    with AdminRestrictedCrudController[BSONObjectID]
    with HasFormShowEqualEditView[User, BSONObjectID]
    with HasBasicListView[User] {

  override protected[controllers] val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "name" -> nonEmptyText,
      "email" -> email,
      "roles" -> seq(text),
      "permissions" -> seq(text),
      "locked" -> boolean
    )(User.apply)(User.unapply))

  override protected val entityNameKey = "user"
  override protected def formatId(id: BSONObjectID) = id.stringify

  override protected val homeCall = routes.UserController.find()

  private val controllerActionNames = DataSetControllerActionNames(
    getMethodNames[DataSetController],
    getMethodNames[DictionaryController],
    getMethodNames[CategoryController],
    getMethodNames[FilterController],
    getMethodNames[DataViewController],
    getMethodNames[StandardClassificationRunController],
    getMethodNames[StandardRegressionRunController],
    getMethodNames[TemporalClassificationRunController],
    getMethodNames[TemporalRegressionRunController]
  )

  // create view and data

  override protected type CreateViewData = (
    Form[User],
    Traversable[DataSpaceMetaInfo],
    DataSetControllerActionNames
  )

  override protected def getFormCreateViewData(form: Form[User]) =
    for {
      all <- dataSpaceMetaInfoRepo.find()
    } yield
      (form, all, controllerActionNames)

  override protected[controllers] def createView = { implicit ctx =>
    (view.create(_,_, _)).tupled
  }

  // edit view and data (= show view)

  override protected type EditViewData = (
    BSONObjectID,
    Form[User],
    Traversable[DataSpaceMetaInfo],
    DataSetControllerActionNames
  )

  override protected def getFormEditViewData(
    id: BSONObjectID,
    form: Form[User]
  ) = { request =>
    for {
      all <- dataSpaceMetaInfoRepo.find()
    } yield
      (id, form, all, controllerActionNames)
  }

  override protected[controllers] def editView = { implicit ctx =>
    (view.edit(_, _, _, _)).tupled
  }

  // list view and data

  override protected def listView = { implicit ctx =>
    (view.list(_, _)).tupled
  }

  // actions

  override protected def saveCall(
    user: User)(
    implicit request: AuthenticatedRequest[AnyContent]
  ): Future[BSONObjectID] = {

    // send an email
    val email = Email(
      "Ada: User Created",
      "Ada Admin <admin@ada-discovery.org>",
      Seq(user.email),
      // sends text, HTML or both...
      bodyText = Some(
        "A new user account has been created." + System.lineSeparator() +
        "You can now log into the Ada Discovery Analytics with this mail address."
      )
//      bodyHtml = Some(s"""<html><body><p>An <b>html</b> A new user account has been created</p></body></html>""")
    )

    mailerClient.send(email)

    // remove repeated permissions
    val userToSave = user.copy(permissions = user.permissions.toSet.toSeq.sorted)

    super.saveCall(userToSave)
  }

  override protected def updateCall(
    user: User)(
    implicit request: AuthenticatedRequest[AnyContent]
  ): Future[BSONObjectID] = {
    // remove repeated permissions
    val userToUpdate = user.copy(permissions = user.permissions.toSet.toSeq.sorted)

    super.updateCall(userToUpdate)
  }

  def listUsersForPermissionPrefix(
    permissionPrefix: Option[String]
  ) = restrictAdminAny(noCaching = true) { implicit request =>
    for {
      allUsers <- repo.find(sort = Seq(AscSort("ldapDn")))
    } yield {
      val filteredUsers = if (permissionPrefix.isDefined)
        allUsers.filter(_.permissions.exists(_.startsWith(permissionPrefix.get)))
      else
        allUsers
      val page = Page(filteredUsers, 0, 0, filteredUsers.size, "ldapDn")
      Ok(view.list(page, Nil))
    }
  }

  def copyPermissions(
    sourceUserId: BSONObjectID,
    targetUserId: BSONObjectID
  ) = restrictAdminAny(noCaching = true) { implicit request =>
    for {
      sourceUser <- repo.get(sourceUserId)
      targetUser <- repo.get(targetUserId)

      userId <- {
        (sourceUser, targetUser).zipped.headOption.map { case (user1, user2) =>
          val userWithMergedPermissions = user2.copy(permissions = user2.permissions ++ user1.permissions)
          repo.update(userWithMergedPermissions).map(Some(_))
        }.getOrElse(
          Future(None)
        )
      }
    } yield
      userId.map { _ =>
        Redirect(homeCall).flashing("success" -> "Permissions successfully copied.")
      }.getOrElse(
        BadRequest(s"User '${sourceUserId.stringify}' or '${targetUserId.stringify}' not found.")
      )
  }

  def idAndNames = restrictAdminAny(noCaching = true) { implicit request =>
    for {
      users <- userRepo.find()
    } yield {
      val idAndNames = users.toSeq.map( user =>
        Json.obj("_id" -> user._id, "name" -> user.ldapDn)
      )
      Ok(JsArray(idAndNames))
    }
  }
}

case class DataSetControllerActionNames(
  dataSetActions: Traversable[String],
  fieldActions: Traversable[String],
  categoryActions: Traversable[String],
  filterActions: Traversable[String],
  dataViewActions: Traversable[String],
  classificationRunActions: Traversable[String],
  regressionRunActions: Traversable[String],
  temporalClassificationRunActions: Traversable[String],
  temporalRegressionRunActions: Traversable[String]
)

object UserDataSetPermissions {

  val viewOnly = Seq(
    "dataSet.getView",
    "dataSet.getDefaultView",
    "dataSet.getWidgets",
    "dataSet.getViewElementsAndWidgetsCallback",
    "dataSet.getNewFilterViewElementsAndWidgetsCallback",
    "dataSet.generateTable",
    "dataSet.getFieldNamesAndLabels",
    "dataSet.getFieldTypeWithAllowedValues",
    "dataSet.getCategoriesWithFieldsAsTreeNodes",
    "dataSet.getFieldValue",
    "dataview.idAndNamesAccessible",
    "filter.idAndNamesAccessible"
  )

  val standard = Seq(
    "dataSet",
    "field.find",
    "category.idAndNames",
    "dataview",
    "filter"
  )
}