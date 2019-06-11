package org.ada.web.controllers

import javax.inject.Inject
import org.ada.web.controllers.core.AdaBaseController
import org.incal.play.controllers.{BaseController, WebContext}
import org.incal.play.security.AuthAction
import play.twirl.api.Html
import views.html.documentation
import play.api.cache.Cached

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DocumentationController @Inject() (cached: Cached) extends AdaBaseController {

  def intro =
    showHtml("intro", documentation.intro()(_))

  def basic =
    showHtml("basic", documentation.basic()(_))

  def stats =
    showHtml("stats", documentation.stats()(_))

  def views =
    showHtml("views", documentation.view()(_))

  def filters =
    showHtml("filters", documentation.filters()(_))

  def ml =
    showHtml("ml", documentation.ml()(_))

  def mlClassification =
    showHtml("mlClassification", documentation.mlClassification()(_))

  def mlRegression =
    showHtml("mlRegression", documentation.mlRegression()(_))

  def mlClusterization =
    showHtml("mlClusterization", documentation.mlClustering()(_))

  def userManagement =
    showHtml("userManagement", documentation.userManagement()(_))

  def dataSetImport =
    showHtml("dataSetImport", documentation.dataSetImport()(_))

  def technology =
    showHtml("technology", documentation.technology()(_))

  private def showHtml(
    cacheName: String,
    html: WebContext => Html
  ) = // cached(s"documentation-$cacheName") ( // TODO: introduce caching only if a user is not logged in
    AuthAction { implicit request =>
      Future(Ok(html(webContext)))
    }
  // )
}