package org.ada.web.controllers.dataset

import org.incal.core.FilterCondition
import reactivemongo.bson.BSONObjectID

abstract class MLRunDispatcher[C <: MLRunController](
  controllerName: ControllerName.Value
) extends DataSetLikeDispatcher[C](controllerName) with MLRunController {

  override protected val noCaching = true

  override def get(id: BSONObjectID) = dispatch(_.get(id))

  override def find(page: Int, orderBy: String, filter: Seq[FilterCondition]) = dispatch(_.find(page, orderBy, filter))

  override def listAll(orderBy: String) = dispatch(_.listAll(orderBy))

  override def create = dispatch(_.create)

  override def delete(id: BSONObjectID) = dispatch(_.delete(id))

  override def exportToDataSet(
    targetDataSetId: Option[String],
    targetDataSetName: Option[String]
  ) = dispatch(_.exportToDataSet(targetDataSetId, targetDataSetName))

  override def exportRecordsAsCsv(
    delimiter: String,
    replaceEolWithSpace: Boolean,
    eol: Option[String],
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ) = dispatch(_.exportRecordsAsCsv(delimiter, replaceEolWithSpace, eol, filter, tableColumnsOnly))

  def exportRecordsAsJson(
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ) = dispatch(_.exportRecordsAsJson(filter, tableColumnsOnly))
}