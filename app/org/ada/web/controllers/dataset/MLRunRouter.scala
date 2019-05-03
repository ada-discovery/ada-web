package org.ada.web.controllers.dataset

import org.incal.play.controllers.ReadonlyRouter
import play.api.mvc.Call
import reactivemongo.bson.BSONObjectID

trait MLRunRouter extends ReadonlyRouter[BSONObjectID] {
  val create: Call
  val delete: (BSONObjectID) => Call
  val exportToDataSet: (Option[String], Option[String]) => Call
  val exportCsv: (String, Boolean, Option[String],Seq[org.incal.core.FilterCondition], Boolean) => Call
  val exportJson: (Seq[org.incal.core.FilterCondition], Boolean) => Call
}