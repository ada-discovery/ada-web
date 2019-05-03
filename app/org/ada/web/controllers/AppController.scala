package org.ada.web.controllers

import javax.inject.Inject
import org.ada.server.models.{DataSpaceMetaInfo, HtmlSnippet, HtmlSnippetId}
import org.incal.core.dataaccess.Criterion._
import play.api.{Configuration, Logger}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import org.ada.web.security.AdaAuthConfig
import org.ada.server.services.UserManager
import org.ada.web.services.DataSpaceService
import org.incal.play.controllers.BaseController
import org.incal.play.security.{AuthAction, SecurityRole}
import org.incal.play.security.SecurityUtil._
import org.ada.server.dataaccess.RepoTypes.HtmlSnippetRepo
import views.html.layout
import play.api.cache.Cached
import play.api.mvc.Action

import scala.concurrent.Future

class AppController @Inject() (
    dataSpaceService: DataSpaceService,
    htmlSnippetRepo: HtmlSnippetRepo,
    val userManager: UserManager,
    cached: Cached
  ) extends BaseController with AdaAuthConfig {

  private val logger = Logger

  def index = AuthAction { implicit request =>
    getHtmlSnippet(HtmlSnippetId.Homepage).map( html =>
      Ok(layout.home(html))
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
  def dataSets = restrictSubjectPresentAnyNoCaching(deadbolt) {
    implicit request =>
      for {
        user <- currentUser(request)
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

          val isAdmin = user.roles.contains(SecurityRole.admin)

          Ok(layout.dataSets(userFirstName, dataSpacesNum, dataSetsNum, metaInfos))
        }.getOrElse(
          BadRequest("No logged user.")
        )
      }
  }
}