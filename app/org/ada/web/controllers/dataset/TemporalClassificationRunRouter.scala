package org.ada.web.controllers.dataset

import org.incal.play.controllers.{GenericJsRouter, GenericRouter}
import scalaz.Scalaz._

class TemporalClassificationRunRouter(dataSetId: String) extends GenericRouter(routes.TemporalClassificationRunDispatcher, "dataSet", dataSetId) with MLRunRouter {
  val list = routes.find _ map route
  val plainList = routeFun(_.find())
  val get = routes.get _ map route
  val create = routeFun(_.create)
  val delete = routes.delete _ map route
  val exportToDataSet = routes.exportToDataSet _ map route
  val exportCsv = routes.exportRecordsAsCsv _ map route
  val exportJson  = routes.exportRecordsAsJson _ map route
}

final class TemporalClassificationRunJsRouter(dataSetId: String) extends GenericJsRouter(routes.javascript.TemporalClassificationRunDispatcher, "dataSet", dataSetId) {
  val launch = routeFun(_.launch)
}