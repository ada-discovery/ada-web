package org.ada.web.controllers.dataset

import org.incal.play.controllers.{GenericJsRouter, GenericRouter}
import scalaz.Scalaz._

class StandardClassificationRunRouter(dataSetId: String) extends GenericRouter(routes.StandardClassificationRunDispatcher, "dataSet", dataSetId) with MLRunRouter {
  val list = routes.find _ map route
  val plainList = routeFun(_.find())
  val get = routes.get _ map route
  val create = routeFun(_.create)
  val delete = routes.delete _ map route
  val exportToDataSet = routes.exportToDataSet _ map route
  val exportCsv = routes.exportRecordsAsCsv _ map route
  val exportJson  = routes.exportRecordsAsJson _ map route
}

final class StandardClassificationRunJsRouter(dataSetId: String) extends GenericJsRouter(routes.javascript.StandardClassificationRunDispatcher, "dataSet", dataSetId) {
  val launch = routeFun(_.launch)
  val selectFeaturesAsAnovaChiSquare = routeFun(_.selectFeaturesAsAnovaChiSquare)
}