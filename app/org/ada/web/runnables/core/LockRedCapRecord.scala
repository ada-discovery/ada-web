package org.ada.web.runnables.core

import javax.inject.Inject
import org.ada.server.models.redcap.LockRecordResponse
import org.ada.server.services.importers.{RedCapLockAction, RedCapServiceFactory}
import org.incal.core.runnables.{InputFutureRunnableExt, RunnableHtmlOutput}
import org.incal.core.util.ReflectionUtil.getCaseClassMemberNamesAndValues

import scala.concurrent.ExecutionContext.Implicits.global

class LockRedCapRecord @Inject()(factory: RedCapServiceFactory) extends InputFutureRunnableExt[LockRedCapRecordSpec] with RunnableHtmlOutput {

  override def runAsFuture(input: LockRedCapRecordSpec) = {
    val redCapService = factory(input.url, input.token)

    for {
      responses <- redCapService.lock(input.action, input.record, input.event, input.instrument)
    } yield {

      def report(prefix: String, responses: Traversable[LockRecordResponse]) = {
        addParagraph(s"<h4>${prefix.capitalize} records #: ${bold(responses.size.toString)}</h4>")
        addOutput("<br/>")
        responses.toSeq.sortBy(_.instrument).foreach { response =>
          addParagraph(bold(s"instruments: ${response.record}"))

          val fieldValues = getCaseClassMemberNamesAndValues(response).filter(_._1 != "record").toSeq.sortBy(_._1)

          fieldValues.foreach { case (fieldName, value) =>
            val stringValue = value match {
              case Some(x) => x.toString
              case None => ""
              case _ => value.toString
            }

            addParagraph(s"$fieldName: ${stringValue}")
          }
          addOutput("<br/>")
        }
        addOutput("<br/>")
      }

      report("locked", responses.filter(_.locked == "1"))
      report("unlocked", responses.filter(_.locked == "0"))
      report("lock-undefined", responses.filter(_.locked == ""))
    }
  }
}

case class LockRedCapRecordSpec(
  url: String,
  token: String,
  action: RedCapLockAction.Value,
  record: String,
  event: Option[String] = None,
  instrument: Option[String] = None
)