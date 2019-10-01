package org.ada.web.controllers

import java.util.concurrent.TimeoutException

import org.incal.core.dataaccess.InCalDataAccessException
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json
import play.api.i18n.Messages.Implicits._
import views.html.{userprofile => views}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Await, Future}
import javax.inject.Inject
import org.ada.server.dataaccess.RepoTypes.UserRepo
import org.ada.web.models.security.DeadboltUser
import org.ada.server.models.User
import reactivemongo.bson.BSONObjectID
import org.ada.web.controllers.core.AdaBaseController

class UserProfileController @Inject() (userRepo: UserRepo) extends AdaBaseController {

  protected val userUpdateForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "name" -> text,
      "email" -> email,
      "roles" -> ignored(Seq[String]()),
      "permissions" -> ignored(Seq[String]()),
      "locked" -> ignored(false)
    )(User.apply)(User.unapply))

  /**
    * Leads to profile page which shows some basic user information.
    */
  def profile = restrictSubjectPresentAny(noCaching = true) { implicit request =>
    Future {
      currentUserFromRequest.map { case DeadboltUser(user) =>
        Ok(views.profile(user))
      }.getOrElse(
        BadRequest("The user has not been logged in.")
      )
    }
  }

  /**
    * Leads to user settings, where the user is allowed to change uncritical user properties such as password, affilitiation, name.
    */
  def settings = restrictSubjectPresentAny(noCaching = true) { implicit request =>
    Future {
      currentUserFromRequest.map { case DeadboltUser(user) =>
        Ok(views.profileSettings(userUpdateForm.fill(user)))
      }.getOrElse(
        BadRequest("The user has not been logged in.")
      )
    }
  }

  /**
    * Save changes made in user settings page to database.
    * Extracts current user from token for information match.
    */
  @Deprecated // TODO: unused?
  def updateSettings = restrictSubjectPresentAny(noCaching = true) { implicit request =>
    currentUserFromRequest.map { case DeadboltUser(user) =>
      userUpdateForm.bindFromRequest.fold(
        { formWithErrors =>
          Future(BadRequest(formWithErrors.errors.toString).flashing("failure" -> "An unexpected error occurred"))
        },
        (newUserData: User) =>
          // we allow only email to be updated
          userRepo.update(user.copy(email = newUserData.email)).map { _ =>
            render {
              case Accepts.Html() => Redirect(routes.UserProfileController.profile()).flashing("success" -> "Profile has been updated")
              case Accepts.Json() => Ok(Json.obj("message" -> "Profile successfully updated"))
            }
          }.recover {
            case t: TimeoutException =>
              Logger.error("Problem found in the update process")
              InternalServerError(t.getMessage)
            case i: InCalDataAccessException =>
              Logger.error("Problem found in the update process")
              InternalServerError(i.getMessage)
          }
        )
      }.getOrElse(
        Future(BadRequest("The user has not been logged in."))
      )
  }
}