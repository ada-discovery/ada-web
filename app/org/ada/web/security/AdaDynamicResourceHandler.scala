package org.ada.web.security

import be.objectify.deadbolt.scala.models.Subject
import be.objectify.deadbolt.scala.{AuthenticatedRequest, DeadboltHandler, DynamicResourceHandler}

import collection.immutable.Map
import play.api.mvc.Request
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * @author Steve Chaloner (steve@objectify.be)
 */
class AdaDynamicResourceHandler extends DynamicResourceHandler {

  override def isAllowed[A](
    name: String,
    meta: Option[Any] = None,
    deadboltHandler: DeadboltHandler,
    request: AuthenticatedRequest[A]
  ): Future[Boolean] =
    for {
      success <- AdaDynamicResourceHandler.handlers(name).isAllowed(name, meta, deadboltHandler, request)
    } yield {
      if (!success)
        Logger.error(s"Unallowed access by [$name].")
      success
    }

  override def checkPermission[A](
    permissionValue: String,
    meta: Option[Any] = None,
    deadboltHandler: DeadboltHandler,
    request: AuthenticatedRequest[A]
  ): Future[Boolean] = Future {
    request.subject.map { subject =>
      val success = subject.permissions.contains(permissionValue)
      if (!success) {
        val username = subject.identifier
        Logger.error(s"Unauthorized access; user [$username] is missing permission [$permissionValue].")
      }
      success
    }.getOrElse {
      Logger.error("Unauthorized access by unregistered user.")
      false
    }
  }
}

object AdaDynamicResourceHandler {
  val handlers: Map[String, DynamicResourceHandler] = Map( )
}