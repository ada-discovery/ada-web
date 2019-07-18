package org.ada.web.controllers.dataset

import javax.inject.Inject

import org.incal.core.FilterCondition
import reactivemongo.bson.BSONObjectID

class CategoryDispatcher @Inject()(
  val dscf: DataSetControllerFactory,
  factory: CategoryControllerFactory
) extends DataSetLikeDispatcher[CategoryController](ControllerName.category)
    with CategoryController {

  override protected val noCaching = true

  override def controllerFactory = factory(_)

  override def get(id: BSONObjectID) = dispatch(_.get(id))

  override def find(page: Int, orderBy: String, filter: Seq[FilterCondition]) = dispatch(_.find(page, orderBy, filter))

  override def listAll(orderBy: String) = dispatch(_.listAll(orderBy))

  override def create = dispatch(_.create)

  override def update(id: BSONObjectID) = dispatch(_.update(id))

  override def edit(id: BSONObjectID) = dispatch(_.edit(id))

  override def delete(id: BSONObjectID) = dispatch(_.delete(id))

  override def save = dispatch(_.save)

  override def saveForName(name: String) = dispatch(_.saveForName(name))

  override def getCategoryD3Root = dispatch(_.getCategoryD3Root)

  override def relocateToParent(id: BSONObjectID, parentId: Option[BSONObjectID]) = dispatchAjax(_.relocateToParent(id, parentId))

  override def idAndNames = dispatchAjax(_.idAndNames)

  override def addFields(categoryId: BSONObjectID, fieldNames: Seq[String]) = dispatchAjax(_.addFields(categoryId, fieldNames))

  override def updateLabel(id: BSONObjectID, label: String) = dispatchAjax(_.updateLabel(id, label))
}