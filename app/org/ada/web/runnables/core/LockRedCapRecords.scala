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
import scala.concurrent.Future.sequence

class LockRedCapRecords @Inject()(
  configuration: Configuration,
  factory: RedCapServiceFactory
) extends InputFutureRunnableExt[LockRedCapRecordsSpec] with RunnableHtmlOutput with InputView[LockRedCapRecordsSpec] {

  private val confPrefix = "runnables.lock_redcap_records."
  private val host = configuration.getString(confPrefix + "host").getOrElse(throwConfigMissing("host"))
  private val token = configuration.getString(confPrefix + "token").getOrElse(throwConfigMissing("token"))
  private val visits = configuration.getObjectList(confPrefix + "visits").getOrElse(throwConfigMissing("visits")).map(
    item => (
      item.get("value").unwrapped().toString,
      item.get("label").unwrapped().toString
    )
  )
  private val excludedInstruments = configuration.getStringSeq(confPrefix + "excluded_instruments").map(_.toSet).getOrElse(Set())

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

    val records = input.records.split(delimiter, -1).toSeq.map(_.trim)

    // aux function to get the lock status of all the records and instruments
    def getLockStatuses =
      sequence(
        records.map { record =>
          redCapService.lock(RedCapLockAction.status, record, Some(input.visit))
        }
      )

    // aux function to perform locking or unlocking on all the instruments
    def handleAllInstruments=
      sequence(
        records.map { record =>
          redCapService.lock(input.action, record, Some(input.visit))
        }
      )

    // aux function to perform locking or unlocking on all but the excluded instruments
    def handleWithoutExcludedInstruments=
      for {
        // get the lock statuses
        statuses <- getLockStatuses

        // collect all the instruments available for each record
        recordInstruments = statuses.flatMap { statuses =>
          statuses.headOption.map { headStatus =>
            (headStatus.record, statuses.map(_.instrument))
          }
        }

        // filter those instruments that should be excluded and perform locking or unlocking on the remaining ones
        _ <- sequence(
          recordInstruments.map { case (record, instruments) =>
            val remainingInstruments = instruments.filter(!excludedInstruments.contains(_))
            sequence(
              remainingInstruments.map(instrument =>
                redCapService.lock(input.action, record, Some(input.visit), Some(instrument))
              )
            )
          }
        )
      } yield ()

    for {
      // lock or unlock the records in parallel
      _ <- if (excludedInstruments.nonEmpty) handleWithoutExcludedInstruments else handleAllInstruments

      // get the status of each record
      responses <- getLockStatuses.map(_.flatten)
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