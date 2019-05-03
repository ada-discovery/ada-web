package org.ada.web.controllers

import javax.inject.Inject

import org.ada.server.services.ldap.{LdapSettings, LdapService}
import org.incal.play.controllers.BaseController
import views.html.ldapviews._
import org.incal.play.security.SecurityUtil._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class LdapUserController @Inject() (
  ldapUserService: LdapService,
  ldapSettings: LdapSettings
) extends BaseController {

  def listAll = restrictAdminAnyNoCaching(deadbolt) {
    implicit request => Future {
      implicit val msg = messagesApi.preferred(request)

      val all = ldapUserService.listUsers
      Ok(userlist(all))
    }
  }

  def get(id: String) = restrictAdminAnyNoCaching(deadbolt) {
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

  def settings = restrictAdminAnyNoCaching(deadbolt) {
    implicit request => Future (
      Ok(views.html.ldapviews.viewSettings(ldapSettings))
    )
  }
}