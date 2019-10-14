package services

import org.ada.server.models.redcap.JsonFormat.responseFormat
import org.ada.server.services.importers.{RedCapLockAction, RedCapServiceFactory}
import org.ada.server.services.GuicePlayTestApp
import org.scalatest._
import play.api.Configuration
import play.api.libs.json.Json

class RedCapServiceTest extends AsyncFlatSpec with Matchers {

//  private val injector = GuicePlayTestApp().injector
//
//  private val redCapServiceFactory = injector.instanceOf[RedCapServiceFactory]
//  private val configuration = injector.instanceOf[Configuration]
//
//  private val redCapService = redCapServiceFactory(
//    configuration.getString("test.redcap.url").get,
//    configuration.getString("test.redcap.token").get
//  )
//
//  "List metadata" should "should return some items" in {
//    redCapService.listMetadatas.map { results =>
//      results.size should be >= 0
//    }
//  }
//
//  "Locking" should "lock the records" in {
//    redCapService.lock(RedCapLockAction.lock, "ND00001").map { results =>
//      println("Response:")
//      println(results.map(x => Json.prettyPrint(Json.toJson(x))).mkString("\n"))
//      results.size should be (1)
//    }
//  }
}