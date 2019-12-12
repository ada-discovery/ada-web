package org.ada.web.controllers.core

import be.objectify.deadbolt.scala.{AuthenticatedRequest, DeadboltHandler}
import org.ada.web.models.security.DeadboltUser
import org.incal.play.controllers.{SecureControllerDispatcher, WithNoCaching}
import org.incal.play.security.ActionSecurity.AuthActionTransformationAny
import org.incal.play.security.AuthAction
import org.incal.play.security.SecurityUtil.toAuthenticatedAction
import play.api.mvc.{Action, AnyContent, Request}
import play.api.routing.Router.Tags.RouteActionMethod
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AdminOrOwnerControllerDispatcherExt[C] {

  this: SecureControllerDispatcher[C] =>

  override type USER = DeadboltUser

  protected def dispatchIsAdminOrPermissionAndOwnerAux(
    objectOwnerId: Request[AnyContent] => Future[Option[BSONObjectID]],
    outputHandler: DeadboltHandler = handlerCache()
  ) = dispatchIsAdminOrPermissionAndOwnerOrPublicAux(
    request => objectOwnerId(request).map((_, false)),
    outputHandler
  )

  protected def dispatchIsAdminOrOwnerAux(
    objectOwnerId: Request[AnyContent] => Future[Option[BSONObjectID]],
    outputHandler: DeadboltHandler = handlerCache()
  ) = dispatchIsAdminOrOwnerOrPublicAux(
    request => objectOwnerId(request).map((_, false)),
    outputHandler
  )

  protected def dispatchIsAdminOrPermissionAndOwnerOrPublicAux(
    objectOwnerIdAndIsPublic: Request[AnyContent] => Future[(Option[BSONObjectID], Boolean)],
    outputHandler: DeadboltHandler = handlerCache()
  ): DispatchActionTransformation = { cAction =>
    AuthAction { implicit request =>
      val actionTransformation = restrictAdminOrPermissionAndUserCustomAny(
        actionPermission(request),
        checkOwnerOrPublic(objectOwnerIdAndIsPublic),
        noCaching,
        outputHandler
      )

      val autAction = toAuthenticatedAction(dispatch(cAction))
      actionTransformation(autAction)(request)
    }
  }

  protected def dispatchIsAdminOrOwnerOrPublicAux(
    objectOwnerIdAndIsPublic: Request[AnyContent] => Future[(Option[BSONObjectID], Boolean)],
    outputHandler: DeadboltHandler = handlerCache()
  ): DispatchActionTransformation = { cAction =>
    AuthAction { implicit request =>
      val actionTransformation = restrictAdminOrUserCustomAny(
        checkOwnerOrPublic(objectOwnerIdAndIsPublic),
        noCaching,
        outputHandler
      )

      val autAction = toAuthenticatedAction(dispatch(cAction))
      actionTransformation(autAction)(request)
    }
  }

  protected def dispatchIsAdmin: DispatchActionTransformation = { cAction =>
    val autAction = toAuthenticatedAction(dispatch(cAction))
    restrictAdminAny(noCaching)(autAction)
  }

  private def checkOwnerOrPublic(
    objectOwnerIdAndIsPublic: Request[AnyContent] => Future[(Option[BSONObjectID], Boolean)]
  ) = { (user: USER, request: AuthenticatedRequest[AnyContent]) =>
    for {
      (objectOwnerIdOption, isPublic) <- objectOwnerIdAndIsPublic(request)
    } yield
      objectOwnerIdOption match {
        case Some(createdById) =>
          // is public or the user accessing the data view is the owner => all is ok
          isPublic || user.id.get.equals(createdById)

        // if the owner not specified then check only if it is public
        case None => isPublic
      }
  }

  private val actionPermission = { request: Request[AnyContent] =>
    val controllerId = getControllerId(request)
    val actionName = request.tags.get(RouteActionMethod).get

    getPermission(controllerId, actionName).getOrElse(
      throw new IllegalArgumentException(s"No permission defined for the action '${actionName}' in the controller '${controllerId}'.")
    )
  }
}