package org.ada.web.controllers.dataset

import org.incal.core.FilterCondition
import javax.inject.Inject

class DictionaryDispatcher @Inject() (
  val dscf: DataSetControllerFactory,
  factory: DictionaryControllerFactory
) extends DataSetLikeDispatcher[DictionaryController](ControllerName.field)
    with DictionaryController {

  override protected val noCaching = true

  override def controllerFactory = factory(_)

  override def get(id: String) = dispatch(_.get(id))

  override def find(page: Int, orderBy: String, filter: Seq[FilterCondition]) = dispatch(_.find(page, orderBy, filter))

  override def listAll(orderBy: String) = dispatch(_.listAll(orderBy))

  override def create = dispatch(_.create)

  override def update(id: String) = dispatch(_.update(id))

  override def edit(id: String) = dispatch(_.edit(id))

  override def delete(id: String) = dispatch(_.delete(id))

  override def save = dispatch(_.save)

  override def updateLabel(id: String, label: String) = dispatchAjax(_.updateLabel(id, label))

  override def exportRecordsAsCsv(
    delimiter : String,
    replaceEolWithSpace: Boolean,
    eol: Option[String],
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ) = dispatch(_.exportRecordsAsCsv(delimiter, replaceEolWithSpace, eol, filter, tableColumnsOnly))

  override def exportRecordsAsJson(
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ) = dispatch(_.exportRecordsAsJson(filter, tableColumnsOnly))

  override def setDefaultLabels = dispatch(_.setDefaultLabels)

  override def convertLabelsToCamelCase = dispatch(_.convertLabelsToCamelCase)
}