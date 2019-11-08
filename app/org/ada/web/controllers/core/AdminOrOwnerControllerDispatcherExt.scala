package org.ada.web.controllers.core

import be.objectify.deadbolt.scala.DeadboltHandler
import org.ada.web.models.security.DeadboltUser
import org.incal.play.controllers.{SecureControllerDispatcher, WithNoCaching}
import org.incal.play.security.AuthAction
import org.incal.play.security.SecurityUtil.toAuthenticatedAction
import play.api.mvc.{Action, AnyContent, Request}
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AdminOrOwnerControllerDispatcherExt[C] {

  this: SecureControllerDispatcher[C] =>

  override type USER = DeadboltUser

  protected def dispatchIsAdminOrOwnerOrPublicAux(
    objectOwnerIdAndIsPublic: Request[AnyContent] => Future[(Option[BSONObjectID], Boolean)],
    outputHandler: DeadboltHandler = handlerCache()
  ): DispatchActionTransformation = { cAction =>
    AuthAction { implicit request =>
      val checkOwner = restrictUserCustomAny(
        { (user, request) =>
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
        },
        noCaching,
        outputHandler
      )

      val actionTransformation = restrictChainAny(Seq(restrictAdminAny(outputHandler = unauthorizedDeadboltHandler), checkOwner))
      val autAction = toAuthenticatedAction(dispatch(cAction))
      actionTransformation(autAction)(request)
    }
  }

  protected def dispatchIsAdminOrOwnerAux(
    objectOwnerId: Request[AnyContent] => Future[Option[BSONObjectID]],
    outputHandler: DeadboltHandler = handlerCache()
  ) = dispatchIsAdminOrOwnerOrPublicAux(
    request => objectOwnerId(request).map((_, false)),
    outputHandler
  )

  protected def dispatchIsAdmin: DispatchActionTransformation = { cAction =>
    val autAction = toAuthenticatedAction(dispatch(cAction))
    restrictAdminAny(noCaching)(autAction)
  }
}