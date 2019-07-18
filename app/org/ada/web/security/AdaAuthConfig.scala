package org.ada.web.security

import be.objectify.deadbolt.scala.models.Role
import org.ada.web.controllers.routes
import jp.t2v.lab.play2.auth.{AuthConfig, _}
import org.ada.server.models.{User => AdaUser}
import play.api.mvc.Results._
import play.api.mvc.{Request, RequestHeader, Result}
import org.ada.server.services.UserManager

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect._

trait AdaAuthConfig extends AuthConfig {

  def userManager: UserManager

  /**
    * A type that is used to identify a user.
    * `String`, `Int`, `Long` and so on.
    */
  type Id = String

  /**
    * Play2-auth specific.
    * Type defintion for User object.
    * Set to AbstractUser, a class extending deadbolt's Subject.
    */
  type User = AdaUser

  /**
    * Play2-auth specific.
    * A type that is defined by every action for authorization.
    * Set to deadbolt's Role class.
    */
  type Authority = Role

  /**
    * A `ClassTag` is used to retrieve an id from the Cache API.
    * Use something like this:
    */
  val idTag: ClassTag[Id] = classTag[Id]

  /**
    * The session timeout in seconds
    */
  val sessionTimeoutInSeconds: Int = 3600

  // useful helper for user extraction from current token
  private def getUserFromToken(request: Request[_]): Future[Option[Id]] = {
    val currentToken: Option[AuthenticityToken] = tokenAccessor.extract(request)
    val userIdFuture = currentToken match {
      case Some(token) => idContainer.get(token)
      case None => Future(None)
    }
    userIdFuture
  }

  // we can't call restoreUser, so we must retrieve the user manually
  def currentUser(request: Request[_]): Future[Option[User]] =
    getUserFromToken(request).flatMap {
      _ match {
        case Some(id) => resolveUser(id)
        case None => Future(None)
      }
    }

  /**
    * A function that returns a `User` object from an `Id`.
    * Retrieves user from Account class.
    */
  override def resolveUser(id: Id)(implicit ctx: ExecutionContext): Future[Option[User]] =
    userManager.findById(id)

  /**
    * Where to redirect the user after a successful login.
    */
  override def loginSucceeded(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    val successfulLoginUrl = request.session.get("successfulLoginUrl")
    Future.successful(
      if (successfulLoginUrl.isDefined && successfulLoginUrl.get.nonEmpty)
        Redirect(successfulLoginUrl.get).withSession("successfulLoginUrl" -> "")
      else
        Redirect(routes.UserProfileController.profile())
    )
  }

  /**
    * Where to redirect the user after logging out
    */
  def logoutSucceeded(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] =
    Future.successful(Redirect(routes.AuthController.loggedOut))

  /**
    * If the user is not logged in and tries to access a protected resource, redirect to login page
    */
  def authenticationFailed(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] =
    Future.successful(Redirect(routes.AuthController.login))

  /**
    * Only used, if play2-auth authorization is required.
    * However, Play2-auth authorization is never used.
    *
    * Shows error message on authorization failure.
    */
  override def authorizationFailed(request: RequestHeader, user: User, authority: Option[Authority])(implicit context: ExecutionContext): Future[Result] = {
    Future.successful(Forbidden("Not authorised. Please change user or login to proceed"))
  }

  /**
    * Unused.
    * This is only used, if play2-auth authorization is required.
    * However, Play2-auth authorization is never used.
    *
    * Maps users to permissions.
    */
  def authorize(user: User, authority: Authority)(implicit ctx: ExecutionContext): Future[Boolean] = Future.successful {
    user.roles.contains(authority.name)
  }

  /**
    * (Optional)
    * You can custom SessionID Token handler.
    * Default implementation use Cookie.
    */
  override lazy val tokenAccessor: CookieTokenAccessor = new CookieTokenAccessor(
    cookieSecureOption = false, // TODO: Introduce   play.api.Play.isProd(play.api.Play.current),
    cookieHttpOnlyOption = true,
    cookieMaxAge       = Some(sessionTimeoutInSeconds)
  )
}