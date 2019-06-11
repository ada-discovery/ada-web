package org.ada.web.models.security

import be.objectify.deadbolt.scala.models.Subject
import org.ada.server.models.User
import org.incal.play.security.{SecurityPermission, SecurityRole}

case class DeadboltUser(user: User) extends Subject {
  override def identifier =
    user.ldapDn

  override def roles =
    user.roles.map(SecurityRole(_)).toList

  override def permissions =
    user.permissions.map(SecurityPermission(_)).toList

  val id = user._id

  val isAdmin = user.roles.contains(SecurityRole.admin)
}
