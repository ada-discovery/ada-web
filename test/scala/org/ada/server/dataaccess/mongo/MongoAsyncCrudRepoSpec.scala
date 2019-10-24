package scala.org.ada.server.dataaccess.mongo

import org.ada.server.dataaccess.mongo.MongoAsyncCrudRepo
import org.ada.server.models.User
import org.scalatest._
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

import scala.org.ada.server.services.Injector

class MongoAsyncCrudRepoSpec extends AsyncFlatSpec with BeforeAndAfter {

  implicit override def executionContext = scala.concurrent.ExecutionContext.Implicits.global

  behavior of "MongoAsyncCrudRepo"

  after {

  }

  it should "can save User" in {
    val repo = new MongoAsyncCrudRepo[User, BSONObjectID]("users")
    repo.reactiveMongoApi = Injector.instanceOf[ReactiveMongoApi]
    val id = BSONObjectID.generate()
    val user = User(Some(id), "testUser", "testUser@testEmail.org", Nil)
    for {
      _ <- repo.save(user)
      entry <- repo.get(id)
      user = entry.getOrElse(fail(s"User with id '$id' not found in DB."))
    } yield succeed
  }
}
