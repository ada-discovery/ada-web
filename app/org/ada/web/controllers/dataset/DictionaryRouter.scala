package org.ada.web.controllers.dataset

import org.incal.play.controllers.{GenericJsRouter, GenericRouter, ReadonlyRouter}
import play.api.mvc.Call
import scalaz.Scalaz._

/**
  * Container for various calls from Controllers.
  * To be passed to other modules like views to simplify data access.
  */
class DictionaryRouter(dataSetId: String) extends GenericRouter(routes.DictionaryDispatcher, "dataSet", dataSetId) with ReadonlyRouter[String] {
  val list = routes.find _ map route
  val plainList =  routeFun(_.find())
  val get = routes.get _ map route
  val save = routeFun(_.save)
  val update = routes.update _ map route
  val updateLabel = routes.updateLabel _ map route
  val exportCsv = routes.exportRecordsAsCsv _ map route
  val exportJson  = routes.exportRecordsAsJson _ map route
  val setDefaultLabels = routeFun(_.setDefaultLabels)
  val convertLabelsToCamelCase = routeFun(_.convertLabelsToCamelCase)
}

final class DictionaryJsRouter(dataSetId: String) extends GenericJsRouter(routes.javascript.DictionaryDispatcher, "dataSet", dataSetId) {
  val updateLabel = routeFun(_.updateLabel)
}