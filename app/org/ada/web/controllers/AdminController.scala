package org.ada.web.controllers

import javax.inject.Inject
import org.ada.server.dataaccess.RepoTypes.DataSpaceMetaInfoRepo
import org.incal.play.controllers.BaseController
import org.ada.server.services.UserManager
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext.Implicits.global

class AdminController @Inject() (
    userManager: UserManager,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo
  ) extends BaseController {

  private val appHomeRedirect = Redirect(routes.AppController.index())

  def importLdapUsers = restrictAdminAny(noCaching = true) {
    implicit request =>
      userManager.synchronizeRepos.map ( _ =>
        appHomeRedirect.flashing("success" -> "LDAP users successfully imported.")
      )
  }

  def purgeMissingLdapUsers = restrictAdminAny(noCaching = true) {
    implicit request =>
      userManager.purgeMissing.map ( _ =>
        appHomeRedirect.flashing("success" -> "Missing users successfully purged.")
      )
  }

  def lockMissingLdapUsers = restrictAdminAny(noCaching = true) {
    implicit request =>
      userManager.lockMissing.map ( _ =>
        appHomeRedirect.flashing("success" -> "Missing users successfully locked.")
      )
  }

  def dataSetIds = restrictAdminAny(noCaching = true) {
    implicit request =>
      for {
        dataSpaces <- dataSpaceMetaInfoRepo.find()
      } yield {
        val dataSetNameLabels = dataSpaces.flatMap(_.dataSetMetaInfos).toSeq.sortBy(_.id).map { dataSetInfo =>
          Json.obj("name" -> dataSetInfo.id , "label" -> dataSetInfo.id)
        }
        Ok(Json.toJson(dataSetNameLabels))
      }
  }
}