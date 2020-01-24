package org.ada.web.controllers

import javax.inject.Inject
import play.api.{Configuration, Logger}
import play.api.Play.current
import play.api.i18n.Messages.Implicits._
import play.api.libs.json._
import play.api.mvc._
import play.api.data.Forms._
import play.api.data._
import org.ada.server.services.UserManager

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits._
import jp.t2v.lab.play2.auth.LoginLogout
import org.ada.web.controllers.core.AdaBaseController
import org.incal.play.security.AuthAction
import org.ada.web.security.AdaAuthConfig
import play.api.libs.mailer.MailerClient

class AuthController @Inject() (
    val userManager: UserManager,
    mailerClient: MailerClient
  ) extends AdaBaseController with LoginLogout with AdaAuthConfig {

  private val logger = Logger

  /**
    * Login form definition.
    */
  private val loginForm = Form {
    tuple(
      "id" -> nonEmptyText,
      "password" -> nonEmptyText
    )
//      .verifying(
//        "Invalid LUMS ID or password",
//        idPassword => Await.result(userManager.authenticate(idPassword._1, idPassword._2), 120000 millis)
//      )
  }

  private val unauthorizedMessage = "It appears that you don't have sufficient rights for access. Please login to proceed."
  private val unauthorizedRedirect = loginRedirect(unauthorizedMessage)

  private def loginRedirect(errorMessage: String) =
    Redirect(routes.AuthController.login).flashing("errors" -> errorMessage)

  /**
    * Redirect to login page.
    */
  def login = AuthAction { implicit request =>
    Future(
      Ok(views.html.auth.login(loginForm))
    )
  }

  /**
    * Remember log out state and redirect to main page.
    */
  def logout = Action.async { implicit request =>
    gotoLogoutSucceeded.map(_.flashing(
      "success" -> "Logged out"
    ).removingFromSession("rememberme"))
  }

  /**
    * Logout for restful api.
    */
  def logoutREST = Action.async { implicit request =>
    gotoLogoutSucceeded(Future(Ok(s"You have been successfully logged out.\n")))
  }

  /**
    * Redirect to logout message page
    */
  def loggedOut = AuthAction { implicit request =>
    Future(
      Ok(views.html.auth.loggedOut())
    )
  }

  /**
    * Check user name and password.
    *
    * @return Redirect to success page (if successful) or redirect back to login form (if failed).
    */
  def authenticate = AuthAction { implicit request =>
    render.async {
      case Accepts.Html() =>
        authenticateAux(
          (formWithErrors: Form[(String, String)]) => BadRequest(views.html.auth.login(formWithErrors)),
          BadRequest(views.html.auth.login(loginForm.withGlobalError("Invalid user id or password"))),
          loginRedirect("User not found or locked."),
          (userId: String) => gotoLoginSucceeded(userId, Future(Redirect(routes.AppController.dataSets())))
        )

      case Accepts.Json() =>
        authenticateAux(
          (formWithErrors: Form[(String, String)]) => BadRequest(formWithErrors.errorsAsJson),
          Unauthorized("Invalid user id or password\n"),
          Unauthorized("User not found or locked.\n"),
          (userId: String) => gotoLoginSucceeded(userId, Future(Ok(s"User '${userId}' successfully logged in. Check the header for a 'PLAY_SESSION' cookie.\n")))
        )
      }
  }

  private def authenticateAux(
    badFormResult: Form[(String, String)] => Result,
    authenticationUnsuccessfulResult: Result,
    userNotFoundOrLockedResult: Result,
    loginSuccessfulResult: String => Future[Result])(
    implicit request: Request[_]
  ): Future[Result] = {
    loginForm.bindFromRequest.fold(
      formWithErrors => Future.successful(badFormResult(formWithErrors)),
      idPassword =>
        for {
          authenticationSuccessful <- userManager.authenticate(idPassword._1, idPassword._2)

          userOption <- userManager.findById(idPassword._1)

          response <-
            if (!authenticationSuccessful) {
              logger.info(s"Unsuccessful login attempt from the ip address: ${request.remoteAddress}")
              Future(authenticationUnsuccessfulResult)
            } else
              userOption match {
                case Some(user) => if (user.locked) Future(userNotFoundOrLockedResult) else loginSuccessfulResult(user.ldapDn)
                case None => Future(userNotFoundOrLockedResult)
              }
        } yield
          response
    )
  }

  // immediately login as basic user
  def loginBasic = Action.async { implicit request =>
    if(userManager.debugUsers.nonEmpty)
      gotoLoginSucceeded(userManager.basicUser.ldapDn, Future(Redirect(routes.AppController.dataSets())))
    else
      Future(unauthorizedRedirect)
  }

  // immediately login as admin user
  def loginAdmin = Action.async { implicit request =>
    if (userManager.debugUsers.nonEmpty)
      gotoLoginSucceeded(userManager.adminUser.ldapDn, Future(Redirect(routes.AppController.dataSets())))
    else
      Future(unauthorizedRedirect)
  }
}