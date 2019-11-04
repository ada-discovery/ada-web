package scala.org.ada.server.dataaccess.mongo

import org.ada.server.dataaccess.mongo.MongoAsyncCrudRepo
import org.ada.server.models.DataSetFormattersAndIds._
import org.ada.server.models.{Category, Dictionary, User}
import org.incal.core.Identity
import org.incal.core.dataaccess.Criterion
import org.incal.core.dataaccess.Criterion.Infix
import org.scalatest._
import play.api.libs.json.Format
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

import scala.concurrent.Future
import scala.org.ada.server.services.InjectorWrapper

class MongoAsyncCrudRepoSpec extends AsyncFlatSpec {

  implicit override def executionContext = scala.concurrent.ExecutionContext.Implicits.global

  def withMongoAsyncCrudRepo[E: Format, ID: Format]
  (testCode: MongoAsyncCrudRepo[E, ID] => Future[Assertion])
  (implicit identity: Identity[E, ID]) = {
    val repo = new MongoAsyncCrudRepo[E, ID]("testCollection")
    repo.reactiveMongoApi = InjectorWrapper.instanceOf[ReactiveMongoApi]
    for {
      _ <- testCode(repo)
      _ <- repo.deleteAll
    } yield succeed
  }

  def assertEntityCanBeSavedAndExists[E: Format, ID: Format]
  (entity: E, repo: MongoAsyncCrudRepo[E, ID])
  (implicit identity: Identity[E, ID]) = {
    val id = identity of entity getOrElse fail
    for {
      _ <- repo.save(entity)
      _ <- repo.flushOps
      exists <- repo.exists(id)
    } yield assert(exists)
  }

  def assertEntityCanBeSavedAndRetrieved[E: Format, ID: Format]
  (entity: E, repo: MongoAsyncCrudRepo[E, ID])
  (implicit identity: Identity[E, ID]) = {
    val id = identity of entity getOrElse fail
    for {
      _ <- repo.save(entity)
      _ <- repo.flushOps
      entry <- repo.get(id)
      retrievedEntity = entry getOrElse fail
    } yield assert(entity == retrievedEntity)
  }

  def assertCreatedEntityCanBeDeleted[E: Format, ID: Format]
  (entity: E, repo: MongoAsyncCrudRepo[E, ID])
  (implicit identity: Identity[E, ID]) = {
    val id = identity of entity getOrElse fail
    for {
      _ <- repo.save(entity)
      _ <- repo.flushOps
      _ <- repo.delete(id)
      _ <- repo.flushOps
      entry <- repo.get(id)
    } yield assert(entry.isEmpty)
  }

  def assertCanFindCreatedEntity[E: Format, ID: Format, T]
  (entity: E, criterion: Seq[Criterion[T]], repo: MongoAsyncCrudRepo[E, ID])
  (implicit identity: Identity[E, ID]) = {
    for {
      _ <- repo.save(entity)
      _ <- repo.flushOps
      entry <- repo.find(criterion)
    } yield assert(entry.nonEmpty)
  }

  def assertCanUpdateCreatedEntity[E: Format, ID: Format]
  (oldEntity: E, newEntity: E, repo: MongoAsyncCrudRepo[E, ID])
  (implicit identity: Identity[E, ID]) = {
    val id = identity of oldEntity getOrElse fail
    for {
      _ <- repo.save(oldEntity)
      _ <- repo.flushOps
      _ <- repo.update(newEntity)
      _ <- repo.flushOps
      entry <- repo.get(id)
      retrievedEntity = entry getOrElse fail
    } yield {
      assert(oldEntity != retrievedEntity)
      assert(newEntity == retrievedEntity)
    }
  }

  def assertCanCountCreatedEntities[E: Format, ID: Format, T]
  (entities: Seq[E], criterion: Seq[Criterion[T]], expectedHits: Int, repo: MongoAsyncCrudRepo[E, ID])
  (implicit identity: Identity[E, ID]) = {
    for {
      _ <- Future.sequence(entities map repo.save)
      count <- repo count criterion
    } yield assert(count == expectedHits)
  }

  behavior of "MongoAsyncCrudRepo"

  it should "save User and check if it exists" in withMongoAsyncCrudRepo[User, BSONObjectID] {
    assertEntityCanBeSavedAndExists(User(Some(BSONObjectID.generate()), "testUser", "testUser@testEmail.org", Nil), _)
  }

  it should "save Dictionary and check if it exists" in withMongoAsyncCrudRepo[Dictionary, BSONObjectID] {
    assertEntityCanBeSavedAndExists(Dictionary(Some(BSONObjectID.generate()), "test", Nil, Nil, Nil, Nil), _)
  }

  it should "save Category and check if it exists" in withMongoAsyncCrudRepo[Category, BSONObjectID] {
    assertEntityCanBeSavedAndExists(Category(Some(BSONObjectID.generate()), "fooCat"), _)
  }


  it should "save and get User" in withMongoAsyncCrudRepo[User, BSONObjectID] {
    assertEntityCanBeSavedAndRetrieved(User(Some(BSONObjectID.generate()), "testUser", "testUser@testEmail.org", Nil), _)
  }


  it should "delete a created User" in withMongoAsyncCrudRepo[User, BSONObjectID] {
    assertCreatedEntityCanBeDeleted(User(Some(BSONObjectID.generate()), "", "", Nil), _)
  }


  it should "find a created User by criteria" in withMongoAsyncCrudRepo[User, BSONObjectID] {
    assertCanFindCreatedEntity(
      User(Some(BSONObjectID.generate()), "testUser", "testUser@testEmail.org", Nil),
      List("email" #== "testUser@testEmail.org"),
      _
    )
  }


  it should "update a created User" in withMongoAsyncCrudRepo[User, BSONObjectID] {
    val user = User(Some(BSONObjectID.generate()), "testUser", "oldEmail", Nil, Nil)
    assertCanUpdateCreatedEntity(user, user.copy(email = "newEmail"), _)
  }


  it should "can count created Users" in withMongoAsyncCrudRepo[User, BSONObjectID] {
    val user1 = User(Some(BSONObjectID.generate()), "testUser1", "testUser1@testEmail.org", Nil)
    val user2 = User(Some(BSONObjectID.generate()), "testUser2", "testUser2@testEmail.org", Nil)
    val user3 = User(Some(BSONObjectID.generate()), "testUser3", "testUser3@testEmail.org", Nil)
    val user4 = User(Some(BSONObjectID.generate()), "testUser4", "", Nil)
    val user5 = User(Some(BSONObjectID.generate()), "testUser5", "", Nil)
    assertCanCountCreatedEntities(List(user1, user2, user3, user4, user5), List("email" #!= ""), 3, _)
  }
}
