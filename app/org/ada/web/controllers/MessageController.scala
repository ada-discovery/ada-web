package org.ada.web.controllers

import javax.inject.Inject
import org.ada.server.models.Message
import org.ada.server.models.Message._
import org.ada.server.dataaccess.RepoTypes.MessageRepo
import play.api.libs.EventSource.EventIdExtractor
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, Controller, Results}
import org.ada.server.models.Message.MessageFormat
import org.ada.web.controllers.core.AdaBaseController
import play.api.libs.EventSource
import reactivemongo.bson.BSONObjectID
import org.incal.core.dataaccess.{DescSort, NotEqualsNullCriterion}
import play.api.http.{ContentTypes, HttpEntity}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits._
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

class MessageController @Inject() (repo: MessageRepo) extends AdaBaseController {

//  private val SCRIPT_REGEX = """<script>(.*)</script>"""
  private val SCRIPT_REGEX = """<script\b[^<]*(?:(?!<\/script\s*>)<[^<]*)*<\/script\s*>"""

  def saveUserMessage(message: String) = restrictSubjectPresentAny() { implicit request =>
    for {
      user <- currentUser()

      response <- user.fold(
        Future(BadRequest("No logged user found"))
      ) { user =>
        val escapedMessage = removeScriptTags(message)
        repo.save(
          Message(None, escapedMessage, Some(user.identifier), user.isAdmin)
        ).map(_=> Ok("Done"))
      }
    } yield
      response
  }

  def listMostRecent(limit: Int) = restrictSubjectPresentAny(noCaching = true) { implicit request =>
    for {
      // the current user
      user <- currentUser()

      // if the user is not admin filter out system messages (without created-by-user)
      criteria = if (!user.map(_.isAdmin).getOrElse(false)) {
        Seq(NotEqualsNullCriterion("createdByUser"))
      } else
        Nil

      // find the messages
      messages <- repo.find(
        criteria = criteria,
        sort = Seq(DescSort("_id")),
        limit = Some(limit)
      )  // ome(0)
    } yield
      Ok(Json.toJson(messages))
  }

  private def eventId(jsObject: JsValue) = Some(((jsObject \ "_id").get.as[BSONObjectID]).stringify)
  private implicit val idExtractor = new EventIdExtractor[JsValue](eventId)

  def eventStream = Action {
    implicit request =>
      val requestStart = new java.util.Date()
      val messageStream = repo.stream.filter(_.timeCreated.after(requestStart)).map(message => Json.toJson(message))
      Ok.chunked(messageStream via EventSource.flow).as(ContentTypes.EVENT_STREAM) // as("text/event-stream")
  }

  private def removeScriptTags(text: String): String = {
    var result = text
    var regexApplied = false
    do {
      val newResult = result.replaceAll(SCRIPT_REGEX, "")
      regexApplied = !result.equals(newResult)
      result = newResult
    } while (regexApplied)
    result
  }
}