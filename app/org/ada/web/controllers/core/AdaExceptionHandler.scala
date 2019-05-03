package org.ada.web.controllers.core

import java.util.concurrent.TimeoutException

import org.ada.web.controllers.routes
import org.ada.server.dataaccess.AdaConversionException
import org.ada.server.AdaException
import org.incal.core.dataaccess.InCalDataAccessException
import org.incal.play.controllers.ExceptionHandler
import play.api.Logger
import play.api.mvc.{Request, Result}
import play.api.mvc.Results.{BadRequest, InternalServerError, Ok, Redirect}

trait AdaExceptionHandler extends ExceptionHandler {

  override protected def handleExceptions(
    functionName: String,
    extraMessage: Option[String] = None)(
    implicit request: Request[_]
  ): PartialFunction[Throwable, Result] = {

    case _: TimeoutException =>
      handleTimeoutException(functionName, extraMessage)

    case e: InCalDataAccessException =>
      val message = s"Repo/db problem found while executing $functionName function${extraMessage.getOrElse("")}."
      Logger.error(message, e)
      Redirect(routes.AppController.index()).flashing("errors" -> message)

    case e: AdaConversionException =>
      val message = s"Conversion problem found while executing $functionName function${extraMessage.getOrElse("")}. Cause: ${e.getMessage}"
      handleBusinessException(message, e)

    case e: AdaException =>
      val message = s"General problem found while executing $functionName function${extraMessage.getOrElse("")}. Cause: ${e.getMessage}"
      handleBusinessException(message, e)

    case e: Throwable =>
      handleFatalException(functionName, extraMessage, e)
  }

  protected def handleExceptionsWithErrorCodes(
    functionName: String,
    extraMessage: Option[String] = None)(
    implicit request: Request[_]
  ): PartialFunction[Throwable, Result] = {

    case e: TimeoutException =>
      val message = s"The request timed out while executing $functionName function${extraMessage.getOrElse("")}."
      Logger.error(message, e)
      InternalServerError(message)

    case e: InCalDataAccessException =>
      val message = s"Repo/db problem found while executing $functionName function${extraMessage.getOrElse("")}."
      Logger.error(message, e)
      InternalServerError(message)

    case e: AdaConversionException =>
      val message = s"Conversion problem found while executing $functionName function${extraMessage.getOrElse("")}. Cause: ${e.getMessage}"
      Logger.warn(message, e)
      BadRequest(message)

    case e: AdaException =>
      val message = s"General problem found while executing $functionName function${extraMessage.getOrElse("")}. Cause: ${e.getMessage}"
      Logger.warn(message, e)
      BadRequest(message)

    case e: Throwable =>
      val message = s"Fatal problem found while executing $functionName function${extraMessage.getOrElse("")}."
      Logger.error(message, e)
      InternalServerError(message)
  }
}