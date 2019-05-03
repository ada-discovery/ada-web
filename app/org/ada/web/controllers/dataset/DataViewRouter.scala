package org.ada.web.controllers.dataset

import org.incal.play.controllers.{CrudRouter, GenericJsRouter, GenericRouter}
import reactivemongo.bson.BSONObjectID
import scalaz.Scalaz._

final class DataViewRouter(dataSetId: String) extends GenericRouter(routes.DataViewDispatcher, "dataSet", dataSetId) with CrudRouter[BSONObjectID] {
  val list = routes.find _ map route
  val plainList = routeFun(_.find())
  val create = routeFun(_.create)
  val get = routes.get _ map route
  val save = routeFun(_.save)
  val update = routes.update _ map route
  val delete = routes.delete _ map route
  val copy = routes.copy _ map route
  val idAndNamesAccessible = routeFun(_.idAndNamesAccessible)
  val getAndShowView = routes.getAndShowView _ map route
  val updateAndShowView = routes.updateAndShowView _ map route
}

final class DataViewJsRouter(dataSetId: String) extends GenericJsRouter(routes.javascript.DataViewDispatcher, "dataSet", dataSetId) {
  val addDistributions = routeFun(_.addDistributions)
  val addDistribution = routeFun(_.addDistribution)
  val addCumulativeCounts = routeFun(_.addCumulativeCounts)
  val addCumulativeCount = routeFun(_.addCumulativeCount)
  val addBoxPlots = routeFun(_.addBoxPlots)
  val addBoxPlot = routeFun(_.addBoxPlot)
  val addBasicStats = routeFun(_.addBasicStats)
  val addScatter = routeFun(_.addScatter)
  val addCorrelation = routeFun(_.addCorrelation)
  val addGridDistribution = routeFun(_.addGridDistribution)
  val addHeatmap = routeFun(_.addHeatmap)
  val addIndependenceTest = routeFun(_.addIndependenceTest)
  val addTableFields = routeFun(_.addTableFields)
  val saveFilter = routeFun(_.saveFilter)
}