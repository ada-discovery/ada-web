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

  protected def dispatchIsAdminOrOwnerOrPublicAux(
    objectOwnerIdAndIsPublic: Request[AnyContent] => Future[Option[(BSONObjectID, Boolean)]],
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
      val objectOwnerIdIsPublicFuture = objectOwnerIdAndIsPublic((request))

      for {
        objectOwnerIdIsPublicOption <- objectOwnerIdIsPublicFuture
        accessingUser <- accessingUserFuture
      } yield {
          objectOwnerIdIsPublicOption match {

            case Some((createdById, isPublic)) =>
              if (isPublic) {
                // is public, all is ok
                toActionAny(action)
              } else
                accessingUser.map { accessingUser =>
                  // if the user accessing the data view is the owner then proceed, otherwise "unauthorized"
                  if (accessingUser._id.get.equals(createdById)) toActionAny(action) else unauthorizedAction
                }.getOrElse(
                  // if we cannot determine the currently logged user for some reason return "unauthorized"
                  unauthorizedAction
                )

            // if the owner (and public flag) not specified return "unauthorized"
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

  protected def dispatchIsAdminOrOwnerAux(
    objectOwnerId: Request[AnyContent] => Future[Option[BSONObjectID]]
  ) = dispatchIsAdminOrOwnerOrPublicAux(request =>
    objectOwnerId(request).map(_.map((_, false))),
    _: Option[DeadboltHandler])(
    _ :C => Action[AnyContent]
  )

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