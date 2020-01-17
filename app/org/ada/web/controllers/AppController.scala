package org.ada.web.controllers

import javax.inject.Inject
import org.ada.server.models.{DataSpaceMetaInfo, HtmlSnippet, HtmlSnippetId, User}
import org.incal.core.dataaccess.Criterion._
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import org.ada.web.services.DataSpaceService
import org.incal.play.security.AuthAction
import org.incal.play.security.SecurityUtil._
import org.ada.server.dataaccess.RepoTypes.HtmlSnippetRepo
import org.ada.web.controllers.core.{AdaBaseController, BSONObjectIDSeqFormatter}
import org.ada.web.models.security.DeadboltUser
import views.html.layout
import play.api.cache.Cached

import scala.concurrent.Future

class AppController @Inject() (
    dataSpaceService: DataSpaceService,
    htmlSnippetRepo: HtmlSnippetRepo,
    cached: Cached
  ) extends AdaBaseController {

  private val logger = Logger

  def index = AuthAction { implicit request =>
    getHtmlSnippet(HtmlSnippetId.Homepage).map( html =>
      Ok(layout.home(html))
    )
  }

  def issues = AuthAction { implicit request =>
    getHtmlSnippet(HtmlSnippetId.Issues).map( html =>
      Ok(layout.issues(html))
    )
  }

  def contact = AuthAction { implicit request =>
    getHtmlSnippet(HtmlSnippetId.Contact).map( html =>
      Ok(layout.contact(html))
    )
  }

  private def getHtmlSnippet(
    id: HtmlSnippetId.Value
  ): Future[Option[String]] =
    htmlSnippetRepo.find(Seq("snippetId" #== id)).map(_.filter(_.active).headOption.map(_.content))

  // TODO: move elsewhere
  def dataSets = restrictSubjectPresentAny(noCaching = true) {
    implicit request =>
      val user = request.subject.collect { case DeadboltUser(user) => user }
      for {
        metaInfos <- user match {
          case None => Future(Traversable[DataSpaceMetaInfo]())
          case Some(user) => dataSpaceService.getTreeForUser(user)
        }
      } yield {
        user.map { user =>
          logger.info("Studies accessed by " + user.ldapDn)
          val dataSpacesNum = metaInfos.map(dataSpaceService.countDataSpacesNumRecursively).sum
          val dataSetsNum = metaInfos.map(dataSpaceService.countDataSetsNumRecursively).sum
          val userFirstName = user.ldapDn.split("\\.", -1).head.capitalize

          Ok(layout.dataSets(userFirstName, dataSpacesNum, dataSetsNum, metaInfos))
        }.getOrElse(
          BadRequest("No logged user.")
        )
      }
  }
}