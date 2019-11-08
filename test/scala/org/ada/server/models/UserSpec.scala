package scala.org.ada.server.models

import org.ada.server.models.User
import org.scalatest._
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID


class UserSpec extends AsyncFlatSpec {

  implicit override def executionContext = scala.concurrent.ExecutionContext.Implicits.global

  behavior of "User"

  it should "be serializable" in {
    val id = BSONObjectID.parse("5dc029c50c00000f059e1ccd") getOrElse fail
    val user = User(Some(id), "userName", "userName@me.com", List("ROLE_A", "ROLE_B"), List("PERM_A", "PERM_B"), true)
    val serial = Json.toJson(user).toString()
    assert(serial == "{\"_id\":{\"$oid\":\"5dc029c50c00000f059e1ccd\"},\"ldapDn\":\"userName\",\"email\":\"userName@me.com\",\"roles\":[\"ROLE_A\",\"ROLE_B\"],\"permissions\":[\"PERM_A\",\"PERM_B\"],\"locked\":true}")
  }

  it should "be de-serializable" in {
    val serial = "{\"_id\":{\"$oid\":\"5dc029c50c00000f059e1ccd\"},\"ldapDn\":\"userName\",\"email\":\"userName@me.com\",\"roles\":[\"ROLE_A\",\"ROLE_B\"],\"permissions\":[\"PERM_A\",\"PERM_B\"],\"locked\":true}"
    val json = Json.parse(serial)
    val user = Json.fromJson[User](json) getOrElse fail
    assert(user._id.getOrElse(fail).stringify == "5dc029c50c00000f059e1ccd")
    assert(user.ldapDn == "userName")
    assert(user.email == "userName@me.com")
    assert(user.roles == List("ROLE_A", "ROLE_B"))
    assert(user.permissions == List("PERM_A", "PERM_B"))
    assert(user.locked)
  }
}
