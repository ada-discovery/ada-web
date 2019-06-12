package org.ada.web.runnables.core

import javax.inject.Inject
import org.ada.server.services.importers.{RedCapLockAction, RedCapServiceFactory}
import org.incal.core.runnables.{InputFutureRunnableExt, RunnableHtmlOutput}
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext.Implicits.global

class RedCapLock @Inject()(factory: RedCapServiceFactory) extends InputFutureRunnableExt[RedCapLockSpec] with RunnableHtmlOutput {

  override def runAsFuture(input: RedCapLockSpec) = {
    val redCapService = factory(input.host, input.token)

    for {
      responses <- redCapService.lock(input.action, input.record, input.event, input.instrument)
    } yield
      responses.foreach { response =>
        addParagraph(Json.prettyPrint(response))
      }
  }
}

case class RedCapLockSpec(
  action: RedCapLockAction.Value,
  host: String,
  token: String,
  record: String,
  event: Option[String] = None,
  instrument: Option[String] = None
)