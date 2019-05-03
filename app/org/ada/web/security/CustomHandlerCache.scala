package org.ada.web.security

import javax.inject.{Inject, Singleton}
import be.objectify.deadbolt.scala.{DeadboltHandler, HandlerKey}
import be.objectify.deadbolt.scala.cache.HandlerCache
import org.ada.web.models.security.DeadboltUser
import org.incal.play.security.DeadboltHandlerKeys
import play.api.mvc.Request
import org.ada.server.services.UserManager

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class CustomHandlerCacheImpl @Inject() (val userManager: UserManager) extends HandlerCache with AdaAuthConfig {

  private val dynamicResourceHandler = new AdaDynamicResourceHandler

  private val currentDeadboltUser = {request: Request[_] => currentUser(request).map(_.map(DeadboltUser(_)))}

  // AdaDeadboltUser
  private val handlers: Map[Any, DeadboltHandler] = Map(
    DeadboltHandlerKeys.default -> new AdaOnFailureRedirectDeadboltHandler(currentDeadboltUser, Some(dynamicResourceHandler)),
    DeadboltHandlerKeys.unauthorizedStatus -> new AdaOnFailureUnauthorizedStatusDeadboltHandler(currentDeadboltUser, Some(dynamicResourceHandler))
  )

  override def apply = handlers(DeadboltHandlerKeys.default)

  override def apply(handlerKey: HandlerKey) = handlers(handlerKey)
}