package scala.org.ada.server.dataaccess.mongo

import org.ada.server.dataaccess.mongo.MongoAsyncCrudRepo
import org.ada.server.models.{Dictionary, User}
import org.ada.server.models.DataSetFormattersAndIds._
import org.incal.core.Identity
import org.incal.core.dataaccess.Criterion.Infix
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

  it should "save User and check if it exists" in withMongoAsyncCrudRepo[User, BSONObjectID] { repo =>
    val id = BSONObjectID.generate()
    val user = User(Some(id), "testUser", "testUser@testEmail.org", List("FOO_ROLE"), List("FOO_PERMISSION"))
    for {
      _ <- repo.save(user)
      _ <- repo.flushOps
      exists <- repo.exists(id)
    } yield assert(exists)
  }

  it should "save and get User" in withMongoAsyncCrudRepo[User, BSONObjectID] { repo =>
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

  it should "delete a created User" in withMongoAsyncCrudRepo[User, BSONObjectID] { repo =>
    val id = BSONObjectID.generate()
    val user = User(Some(id), "", "", Nil)
    for {
      _ <- repo.save(user)
      _ <- repo.flushOps
      _ <- repo.delete(id)
      _ <- repo.flushOps
      entry <- repo.get(id)
    } yield assert(entry.isEmpty)
  }

  it should "find a created User by criteria" in withMongoAsyncCrudRepo[User, BSONObjectID] { repo =>
    val id = BSONObjectID.generate()
    val user = User(Some(id), "testUser", "testUser@testEmail.org", List("FOO_ROLE"), List("FOO_PERMISSION"))
    for {
      _ <- repo.save(user)
      _ <- repo.flushOps
      entry <- repo.find(List("email" #== user.email))
    } yield assert(entry.nonEmpty)
  }

  it should "update a created User" in withMongoAsyncCrudRepo[User, BSONObjectID] { repo =>
    val id = BSONObjectID.generate()
    val user = User(Some(id), "testUser", "oldEmail", Nil, Nil)
    for {
      _ <- repo.save(user)
      _ <- repo.flushOps
      _ <- repo.update(user.copy(email = "newEmail"))
      _ <- repo.flushOps
      entry <- repo.get(id)
      retrievedUser = entry.getOrElse(fail(s"User with id '$id' not found in DB."))
    } yield {
      assert(retrievedUser.email != "oldEmail")
      assert(retrievedUser.email == "newEmail")
    }
  }

  it should "can count created Users" in withMongoAsyncCrudRepo[User, BSONObjectID] { repo =>
    val user1 = User(Some(BSONObjectID.generate()), "testUser1", "testUser1@testEmail.org", Nil)
    val user2 = User(Some(BSONObjectID.generate()), "testUser2", "testUser2@testEmail.org", Nil)
    val user3 = User(Some(BSONObjectID.generate()), "testUser3", "testUser3@testEmail.org", Nil)
    val user4 = User(Some(BSONObjectID.generate()), "testUser4", "", Nil)
    val user5 = User(Some(BSONObjectID.generate()), "testUser5", "", Nil)
    for {
      _ <- repo.save(user1)
      _ <- repo.save(user2)
      _ <- repo.save(user3)
      _ <- repo.save(user4)
      _ <- repo.save(user5)
      _ <- repo.flushOps
      count <- repo.count(List("email" #!= ""))
    } yield assert(count == 3)
  }

  it should "save and get Dictionary" in withMongoAsyncCrudRepo[Dictionary, BSONObjectID] { repo =>
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
