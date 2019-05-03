package org.ada.web.controllers.core

import be.objectify.deadbolt.scala.{AuthenticatedRequest, DeadboltHandler}
import org.ada.server.models.User
import org.incal.play.controllers.SecureControllerDispatcher
import org.incal.play.security.SecurityRole
import org.incal.play.security.SecurityUtil.{AuthenticatedAction, restrictChainFuture2, toActionAny}
import play.api.mvc.{Action, AnyContent, Request}
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AdminOrOwnerControllerDispatcherExt[C] {

  this: SecureControllerDispatcher[C] =>

  protected def dispatchIsAdminOrOwnerAux(
    objectOwnerId: Request[AnyContent] => Future[Option[BSONObjectID]],
    outputDeadboltHandler: Option[DeadboltHandler])(
    action: C => Action[AnyContent]
  ) = Action.async { implicit request =>
    val originalAction = dispatchAuthenticated(action)

    val outputHandler = outputDeadboltHandler.getOrElse(deadboltHandlerCache())

    // check if the view owner matches a currently logged user
    def checkOwner = { action: AuthenticatedAction[AnyContent] =>
      val unauthorizedAction: Action[AnyContent] =
        toActionAny{ implicit req: AuthenticatedRequest[AnyContent] => outputHandler.onAuthFailure(req) }

      val accessingUserFuture = currentUser(request)
      val objectOwnerIdFuture = objectOwnerId((request))

      for {
        objectOwnerId <- objectOwnerIdFuture
        accessingUser <- accessingUserFuture
      } yield {
        objectOwnerId match {
          case Some(createdById) =>
            accessingUser.map { accessingUser =>
              // if the user accessing the data view is the owner then  process, otherwise "unauthorized"
              if (accessingUser._id.get.equals(createdById)) toActionAny(action) else unauthorizedAction
            }.getOrElse(
              // if we cannot determine the currently logged user for some reason return "unauthorized"
              unauthorizedAction
            )
          case None => unauthorizedAction
        }
      }
    }

    // is admin?
    def isAdmin = { action: AuthenticatedAction[AnyContent] =>
      Future(
        deadbolt.Restrict[AnyContent](List(Array(SecurityRole.admin)), unauthorizedDeadboltHandler)()(action)
      )
    }

    val extraRestrictions = restrictChainFuture2(Seq(isAdmin, checkOwner))_
    extraRestrictions(originalAction)(request)
  }

  protected def dispatchIsAdmin(
    action: C => Action[AnyContent]
  ) = Action.async { implicit request =>
    val originalAction = dispatchAuthenticated(action)

    // is admin?
    def isAdmin = { action: AuthenticatedAction[AnyContent] =>
      Future(
        deadbolt.Restrict[AnyContent](List(Array(SecurityRole.admin)), unauthorizedDeadboltHandler)()(action)
      )
    }

    val extraRestrictions = restrictChainFuture2(Seq(isAdmin))_
    extraRestrictions(originalAction)(request)
  }

  protected def currentUser(request: Request[_]): Future[Option[User]]
}