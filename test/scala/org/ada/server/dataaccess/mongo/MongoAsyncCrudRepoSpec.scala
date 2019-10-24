package scala.org.ada.server.dataaccess.mongo

import org.ada.server.dataaccess.mongo.MongoAsyncCrudRepo
import org.ada.server.models.User
import org.incal.core.Identity
import org.scalatest._
import play.api.libs.json.Format
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

import scala.concurrent.Future
import scala.org.ada.server.services.Injector

class MongoAsyncCrudRepoSpec extends AsyncFlatSpec {

  implicit override def executionContext = scala.concurrent.ExecutionContext.Implicits.global

  def withMongoAsyncCrudRepo[E: Format, ID: Format](testCode: MongoAsyncCrudRepo[E, ID] => Future[Assertion])
                                                   (implicit identity: Identity[E, ID]) = {
    val repo = new MongoAsyncCrudRepo[E, ID]("testCollection")
    repo.reactiveMongoApi = Injector.instanceOf[ReactiveMongoApi]
    try {
      testCode(repo)
    } finally {
      repo.deleteAll
    }
  }

  behavior of "MongoAsyncCrudRepo"

  it should "can save User" in withMongoAsyncCrudRepo[User, BSONObjectID] { repo =>
    val id = BSONObjectID.generate()
    val user = User(Some(id), "testUser", "testUser@testEmail.org", Nil)
    for {
      _ <- repo.save(user)
      _ <- repo.flushOps
      entry <- repo.get(id)
      retrievedUser = entry.getOrElse(fail(s"User with id '$id' not found in DB."))
    } yield {
      assert(retrievedUser._id.contains(id))
      assert(retrievedUser.ldapDn == "testUser")
      assert(retrievedUser.email == "testUser@testEmail.org")
    }
  }
}
