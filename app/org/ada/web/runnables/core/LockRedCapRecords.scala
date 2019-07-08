package org.ada.web.runnables.core

import javax.inject.Inject
import org.ada.server.AdaException
import org.ada.server.models.redcap.LockRecordResponse
import org.ada.server.services.importers.{RedCapLockAction, RedCapServiceFactory}
import org.ada.web.runnables.InputView
import org.incal.core.runnables.{InputFutureRunnableExt, RunnableHtmlOutput}
import play.api.Configuration
import org.incal.core.util.GroupMapList
import org.incal.play.controllers.WebContext
import org.incal.play.controllers.WebContext._
import views.html.elements.{select, textarea}
import org.ada.web.util.enumToValueString

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class LockRedCapRecords @Inject()(
  configuration: Configuration,
  factory: RedCapServiceFactory
) extends InputFutureRunnableExt[LockRedCapRecordsSpec] with RunnableHtmlOutput with InputView[LockRedCapRecordsSpec] {

  private val host = configuration.getString("runnables.lock_redcap_records.host").getOrElse(throwConfigMissing("host"))
  private val token = configuration.getString("runnables.lock_redcap_records.token").getOrElse(throwConfigMissing("token"))

  private val visits = configuration.getObjectList("runnables.lock_redcap_records.visits").getOrElse(throwConfigMissing("visits")).map(
    item => (
      item.get("value").unwrapped().toString,
      item.get("label").unwrapped().toString
    )
  )

  private def throwConfigMissing(entryName: String) =
    throw new AdaException(s"No REDCap $entryName defined. Please set 'runnables.lock_redcap_records.$entryName' in your config file (custom.conf).")

  override def runAsFuture(input: LockRedCapRecordsSpec) = {
    val redCapService = factory(host, token)

    import RedCapRecordDelimiter._

    val delimiter = input.delimiter match {
      case Comma => ","
      case Tab => "\t"
      case NewLine => "\n"
    }

    for {
      _ <- Future.sequence(
        input.records.split(delimiter, -1).toSeq.map { record =>
          redCapService.lock(input.action, record.trim, Some(input.visit))
        }
      )

      responses <- Future.sequence(
        input.records.split(delimiter, -1).toSeq.map { record =>
          redCapService.lock(RedCapLockAction.status, record.trim, Some(input.visit))
        }
      ).map(_.flatten)
    } yield {
      def report(prefix: String, responses: Traversable[LockRecordResponse]) = {
        val recordResponses = responses.map(response => (response.record, response)).toGroupMap.toSeq.sortBy(_._1)

        addParagraph(s"<h4>${prefix.capitalize} instruments #: ${bold(responses.size.toString)}</h4>")
        recordResponses.map { case (record, responses) =>
          addParagraph(s"- ${record}: ${bold(responses.size.toString)}")
        }
        addParagraph("<br/>")
      }

      report("locked", responses.filter(_.locked == "1"))
      report("unlocked", responses.filter(_.locked == "0"))
      report("lock-undefined", responses.filter(_.locked == ""))
    }
  }

  override def inputFields(
    implicit webContext: WebContext
  ) =  (form) => html(
    textarea("lockRedCapRecords", "records", form, Seq('cols -> 20, 'rows -> 5)),
    select("lockRedCapRecords", "delimiter", form, enumToValueString(RedCapRecordDelimiter), false),
    select("lockRedCapRecords", "visit", form, visits, false),
    select("lockRedCapRecords", "action", form, enumToValueString(RedCapLockAction), false)
  )
}

case class LockRedCapRecordsSpec(
  records: String,
  visit: String,
  delimiter: RedCapRecordDelimiter.Value,
  action: RedCapLockAction.Value
)

object RedCapRecordDelimiter extends Enumeration {
  val Comma, Tab, NewLine = Value
}