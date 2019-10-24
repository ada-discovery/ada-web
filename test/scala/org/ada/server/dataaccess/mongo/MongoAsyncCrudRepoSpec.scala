package scala.org.ada.server.dataaccess.mongo

import org.ada.server.dataaccess.mongo.MongoAsyncCrudRepo
import org.ada.server.models.{Dictionary, User}
import org.ada.server.models.DataSetFormattersAndIds._
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
    for {
      _ <- testCode(repo)
      _ <- repo.deleteAll
    } yield succeed
  }

  behavior of "MongoAsyncCrudRepo"

  it should "save User" in withMongoAsyncCrudRepo[User, BSONObjectID] { repo =>
    val id = BSONObjectID.generate()
    val user = User(Some(id), "testUser", "testUser@testEmail.org", List("FOO_ROLE"), List("FOO_PERMISSION"))
    for {
      _ <- repo.save(user)
      _ <- repo.flushOps
      entry <- repo.get(id)
      retrievedUser = entry.getOrElse(fail(s"User with id '$id' not found in DB."))
    } yield {
      assert(retrievedUser._id.contains(id))
      assert(retrievedUser.ldapDn == user.ldapDn)
      assert(retrievedUser.email == user.email)
      assert(retrievedUser.locked == user.locked)
      assert(retrievedUser.permissions == user.permissions)
      assert(retrievedUser.roles == user.roles)
    }
  }

  it should "save Dictionary" in withMongoAsyncCrudRepo[Dictionary, BSONObjectID] { repo =>
    val id = BSONObjectID.generate()
    val dictionary = Dictionary(Some(id), "test", Nil, Nil, Nil, Nil)  // #TODO: Get a bit more creative here
    for {
      _ <- repo.save(dictionary)
      _ <- repo.flushOps
      entry <- repo.get(id)
      retrievedDictionary = entry.getOrElse(fail(s"Dictionary with id '$id' not found in DB."))
    } yield {
      assert(retrievedDictionary._id.contains(id))
      assert(retrievedDictionary.dataSetId == dictionary.dataSetId)
      assert(retrievedDictionary.categories == dictionary.categories)
      assert(retrievedDictionary.dataviews == dictionary.dataviews)
      assert(retrievedDictionary.fields == dictionary.fields)
      assert(retrievedDictionary.filters == dictionary.filters)
    }
  }
}
