package org.ada.web.controllers.dataset

import org.incal.play.controllers.{CrudRouter, GenericJsRouter, GenericRouter}
import reactivemongo.bson.BSONObjectID
import scalaz.Scalaz._

final class CategoryRouter(dataSetId: String) extends GenericRouter(routes.CategoryDispatcher, "dataSet", dataSetId) with CrudRouter[BSONObjectID] {
  val list = routes.find _ map route
  val plainList = routeFun(_.find())
  val get = routes.get _ map route
  val create = routeFun(_.create)
  val save = routeFun(_.save)
  val saveForName = routes.saveForName _ map route
  val update = routes.update _ map route
  val delete = routes.delete _ map route
  val getCategoryD3Root= routeFun(_.getCategoryD3Root)
  val relocateToParent = routes.relocateToParent _ map route
  val idAndNames = routeFun(_.idAndNames)
}

final class CategoryJsRouter(dataSetId: String) extends GenericJsRouter(routes.javascript.CategoryDispatcher, "dataSet", dataSetId) {
  val get = routeFun(_.get)
  val saveForName = routeFun(_.saveForName)
  val relocateToParent = routeFun(_.relocateToParent)
  val addFields = routeFun(_.addFields)
  val updateLabel = routeFun(_.updateLabel)
}