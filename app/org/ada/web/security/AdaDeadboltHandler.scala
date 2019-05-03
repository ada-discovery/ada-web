package org.ada.web.security

import org.ada.web.controllers.routes
import play.api.mvc._
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import Results._
import be.objectify.deadbolt.scala.{AuthenticatedRequest, DeadboltHandler, DynamicResourceHandler}
import be.objectify.deadbolt.scala.models.Subject
import io.netty.handler.codec.http.HttpHeaders.Names
import org.incal.play.util.WebUtil.redirectToRefererOrElse

/**
  * Hooks for deadbolt
  */
class AdaOnFailureRedirectDeadboltHandler(
    getCurrentUser: Request[_] => Future[Option[Subject]],
    dynamicResourceHandler: Option[DynamicResourceHandler] = None
  ) extends AdaDeadboltHandler(getCurrentUser, dynamicResourceHandler) {

  /**
    * TODO: execute more meaningful action.
    * Action if user is not authorized.
    * Redirects user to login form if not authorized.
    *
    * @param request request leading to failure.
    * @return Redirect to login form.
    */
  override def onAuthFailure[A](request: AuthenticatedRequest[A]): Future[Result] =
    getSubject(request).map {
      _ match {
        case Some(subject) => {
          println("Accepted encodings: " + getAcceptedEncodings(request).mkString(","))

          val username = subject.identifier
          Logger.error(s"Unauthorized access by '$username' to '${request.uri}'.")
          val result = redirectToRefererOrElse(routes.AppController.index())(request)
          result.flashing("errors" -> "Access denied! We're sorry, but you are not authorized to perform the requested operation.")
        }

        case None =>
          Redirect(routes.AuthController.login).withSession("successfulLoginUrl" -> request.uri)
      }
    }

  private def getAcceptedEncodings(request: Request[_]) =
    request.headers.get(Names.ACCEPT_ENCODING).map(_.split(',').map(_.trim)).getOrElse(Array())
}

class AdaOnFailureUnauthorizedStatusDeadboltHandler(
    getCurrentUser: Request[_] => Future[Option[Subject]],
    dynamicResourceHandler: Option[DynamicResourceHandler] = None
  ) extends AdaDeadboltHandler(getCurrentUser, dynamicResourceHandler) {

  override def onAuthFailure[A](request: AuthenticatedRequest[A]): Future[Result] =
    Future(Unauthorized)
}

protected abstract class AdaDeadboltHandler(
    getCurrentUser: Request[_] => Future[Option[Subject]],
    dynamicResourceHandler: Option[DynamicResourceHandler] = None
  ) extends DeadboltHandler {

  /**
    * Pre-authorization task. May block further execution.
    * Empty right now.
    *
    * @param request
    * @return
    */
  override def beforeAuthCheck[A](request: Request[A]): Future[Option[Result]] =
    Future(None)

  /**
    * Hook for dynamic constraint types.
    *
    * @param request
    * @return
    */
  override def getDynamicResourceHandler[A](request: Request[A]): Future[Option[DynamicResourceHandler]] = {
    Future(dynamicResourceHandler.orElse(Some(new AdaDynamicResourceHandler())))
  }

  /**
    * Retrieves the current user, wrapped in an Option.
    *
    * @param request Current request.
    * @return Current user, if logged in. None otherwise.
    */
  override def getSubject[A](request: AuthenticatedRequest[A]): Future[Option[Subject]] =
    getCurrentUser(request)
}