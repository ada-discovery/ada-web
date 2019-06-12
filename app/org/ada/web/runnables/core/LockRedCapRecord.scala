package org.ada.web.runnables.core

import javax.inject.Inject
import model.OptionalRobustIntFormat
import org.ada.server.services.importers.{RedCapLockAction, RedCapServiceFactory}
import org.incal.core.runnables.{InputFutureRunnableExt, RunnableHtmlOutput}
import play.api.libs.json.{Format, Json}
import play.api.libs.functional.syntax._
import play.api.libs.json._
import org.incal.core.util.ReflectionUtil.getCaseClassMemberNamesAndValues

import scala.concurrent.ExecutionContext.Implicits.global

class LockRedCapRecord @Inject()(factory: RedCapServiceFactory) extends InputFutureRunnableExt[LockRedCapRecordSpec] with RunnableHtmlOutput {

  implicit val responseFormat: Format[LockRedCapRecordResponse] = (
    (__ \ "record").format[String] and
    (__ \ "redcap_event_name").format[String] and
    (__ \ "instrument").format[String] and
    (__ \ "instance").format[Option[Int]](OptionalRobustIntFormat) and
    (__ \ "locked").format[String] and
    (__ \ "username").formatNullable[String] and
    (__ \ "timestamp").formatNullable[String]
  ) (LockRedCapRecordResponse.apply, unlift(LockRedCapRecordResponse.unapply))

  override def runAsFuture(input: LockRedCapRecordSpec) = {
    val redCapService = factory(input.url, input.token)

    for {
      jsons <- redCapService.lock(input.action, input.record, input.event, input.instrument)
    } yield {
      val responses = jsons.map(_.as[LockRedCapRecordResponse])

      def report(prefix: String, responses: Traversable[LockRedCapRecordResponse]) = {
        addParagraph(s"<h4>${prefix.capitalize} records #: ${bold(responses.size.toString)}</h4>")
        addOutput("<br/>")
        responses.toSeq.sortBy(_.instrument).foreach { response =>
          addParagraph(bold(s"record: ${response.record}"))

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

case class LockRedCapRecordResponse(
  record: String,
  redcap_event_name: String,
  instrument: String,
  instance: Option[Int],
  locked: String,
  username: Option[String],
  timestamp: Option[String]
)