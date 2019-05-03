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
import org.ada.web.models.security.DeadboltUser
import org.ada.server.models.User
import org.incal.play.controllers.BaseController
import reactivemongo.bson.BSONObjectID
import org.incal.play.security.SecurityUtil.restrictSubjectPresentAnyNoCaching
import org.ada.server.services.UserManager

class UserProfileController @Inject() (userManager: UserManager) extends BaseController {

  protected val userUpdateForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "name" -> text,
      "email" -> ignored("placeholder"),
      //"LDAP DN" -> ignored("placeholder"),
      //"affiliation" -> text,
      "roles" -> ignored(Seq[String]()),
      "permissions" -> ignored(Seq[String]())
    )(User.apply)(User.unapply))

  /**
    * Leads to profile page which shows some basic user information.
    */
  def profile() = restrictSubjectPresentAnyNoCaching(deadbolt) {
    implicit request => Future {
      request.subject.map { case DeadboltUser(user) =>
        Ok(views.profile(user))
      }.getOrElse(
        BadRequest("The user has not been logged in.")
      )
    }
  }

  /**
    * Leads to user settings, where the user is allowed to change uncritical user properties such as password, affilitiation, name.
    */
  def settings() = restrictSubjectPresentAnyNoCaching(deadbolt) {
    implicit request => Future {
      request.subject.map { case DeadboltUser(user) =>
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
  def updateSettings() = restrictSubjectPresentAnyNoCaching(deadbolt) {
    implicit request =>
      request.subject.map { case DeadboltUser(user) =>
        userUpdateForm.bindFromRequest.fold(
          { formWithErrors =>
            Future(BadRequest(formWithErrors.errors.toString).flashing("failure" -> "An unexpected error occured"))
          },
          (newUserData: User) => {
            updateUserCall(user, newUserData).map { _ =>
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
          })
      }.getOrElse(
        Future(BadRequest("The user has not been logged in."))
      )
  }

  /**
    * Updates user data by setting unprotected fields to new values.
    *
    * @param refData Reference to fill in for protected fields.
    * @param newData New values, with _id, email, password, roles, permissions being ignored.
    * @return Future(true), if user successfully found and updated in database/ usermanager.
    */
  protected def updateUserCall(refData: User, newData: User): Future[Boolean] =
    userManager.updateUser(newData)
}