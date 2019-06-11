package org.ada.web.controllers

import java.util.Date

import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.server.AdaException
import org.ada.server.models.{Filter, HtmlSnippet, HtmlSnippetId}
import org.ada.server.models.HtmlSnippet._
import org.incal.core.dataaccess.Criterion._
import org.incal.play.controllers.{AdminRestrictedCrudController, HasBasicFormCrudViews}
import org.incal.play.formatters.EnumFormatter
import org.incal.play.security.AuthAction
import org.ada.server.dataaccess.RepoTypes._
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.__
import play.api.mvc.{Action, AnyContent, Request}
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import views.html.{layout, htmlSnippet => view}

import scala.concurrent.Future

class HtmlSnippetController @Inject() (
    htmlSnippetRepo: HtmlSnippetRepo
  ) extends AdaCrudControllerImpl[HtmlSnippet, BSONObjectID](htmlSnippetRepo)
    with AdminRestrictedCrudController[BSONObjectID]
    with HasBasicFormCrudViews[HtmlSnippet, BSONObjectID] {

  private implicit val htmlSnippedIdFormatter = EnumFormatter(HtmlSnippetId)

  override protected[controllers] val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "snippetId" -> of[HtmlSnippetId.Value],
      "content" -> nonEmptyText,
      "active" -> boolean,
      "createdById" -> ignored(Option.empty[BSONObjectID]),
      "timeCreated" -> default(date("yyyy-MM-dd HH:mm:ss"), new Date())
    )(HtmlSnippet.apply)(HtmlSnippet.unapply))

  override protected val homeCall = routes.HtmlSnippetController.find()

  override protected def createView = { implicit ctx => view.create(_) }

  override protected def showView = editView

  override protected def editView = { implicit ctx => view.edit(_) }

  override protected def listView = { implicit ctx => (view.list(_, _)).tupled }

  override def saveCall(
    htmlSnippet: HtmlSnippet)(
    implicit request: AuthenticatedRequest[AnyContent]
  ): Future[BSONObjectID] =
    for {
      user <- currentUser()
      id <- {
        val htmlSnippetWithUser = user match {
          case Some(user) => htmlSnippet.copy(createdById = user.id)
          case None => throw new AdaException("No logged user found")
        }
        repo.save(htmlSnippetWithUser)
      }
    } yield
      id

  override protected def updateCall(
    htmlSnippet: HtmlSnippet)(
    implicit request: AuthenticatedRequest[AnyContent]
  ): Future[BSONObjectID] =
    for {
      existingHtmlSnippetOption <- repo.get(htmlSnippet._id.get)

      id <- {
        val mergedHtmlSnippet =
          existingHtmlSnippetOption.fold(htmlSnippet) { existingHtmlSnippet =>
            htmlSnippet.copy(
              createdById = existingHtmlSnippet.createdById,
              timeCreated = existingHtmlSnippet.timeCreated
            )
          }

        repo.update(mergedHtmlSnippet)
      }
    } yield
      id

  def getHtmlLinks = AuthAction { implicit request =>
    getHtmlSnippet(HtmlSnippetId.Links).map(html =>
      Ok(html.getOrElse(""))
    )
  }

  private def getHtmlSnippet(
    id: HtmlSnippetId.Value
  ): Future[Option[String]] =
    htmlSnippetRepo.find(Seq("snippetId" #== id)).map(_.filter(_.active).headOption.map(_.content))
}