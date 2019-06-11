package org.ada.web.controllers

import javax.inject.Inject
import org.ada.server.services.ldap.{LdapService, LdapSettings}
import org.ada.web.controllers.core.AdaBaseController
import org.incal.play.controllers.BaseController
import views.html.ldapviews._
import org.incal.play.security.SecurityUtil._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class LdapUserController @Inject() (
  ldapUserService: LdapService,
  ldapSettings: LdapSettings
) extends AdaBaseController {

  def listAll = restrictAdminAny(noCaching = true) {
    implicit request => Future {
      implicit val msg = messagesApi.preferred(request)

      val all = ldapUserService.listUsers
      Ok(userlist(all))
    }
  }

  def get(id: String) = restrictAdminAny(noCaching = true) {
    implicit request => Future {
      implicit val msg = messagesApi.preferred(request)

      val userOption = ldapUserService.listUsers.find{entry => (entry.uid == id)}
      userOption.fold(
        BadRequest(s"LDAP user with id '$id' not found.")
      ) {
        user => Ok(usershow(user))
      }
    }
  }

  def settings = restrictAdminAny(noCaching = true) {
    implicit request => Future (
      Ok(views.html.ldapviews.viewSettings(ldapSettings))
    )
  }
}