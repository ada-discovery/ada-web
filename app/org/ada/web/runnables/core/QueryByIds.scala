package org.ada.web.runnables.core

import scala.concurrent.ExecutionContext.Implicits.global
import runnables.DsaInputFutureRunnable
import org.incal.core.runnables.RunnableHtmlOutput
import org.ada.server.models.DataSetFormattersAndIds.JsObjectIdentity
import org.incal.core.dataaccess.Criterion._
import org.incal.core.dataaccess._
import reactivemongo.bson.BSONObjectID
import play.api.libs.json.Json

class QueryByIds extends DsaInputFutureRunnable[QueryByIdsSpec] with RunnableHtmlOutput with IdCriterionHelper{

  override def runAsFuture(input: QueryByIdsSpec) =
    for {
      jsons <- createDsa(input.dataSetId).dataSetRepo.find(Seq(criterion(input.ids, input.negate)))
    } yield {
      addParagraph(s"Found <b>${jsons.size}</b> items:<br/>")
      jsons.foreach { json =>
        addParagraph(Json.stringify(json))
      }
    }
}

trait IdCriterionHelper {

  def criterion(
    ids: Seq[BSONObjectID],
    negate: Boolean
  ): Criterion[_] =
    ids.size match {
      case 0 => if (negate) NotEqualsNullCriterion(JsObjectIdentity.name) else EqualsNullCriterion(JsObjectIdentity.name)
      case 1 => if (negate) JsObjectIdentity.name #!= ids.head else JsObjectIdentity.name #== ids.head
      case _ => if (negate) JsObjectIdentity.name #!-> ids else JsObjectIdentity.name #-> ids
    }
}

case class QueryByIdsSpec(
  dataSetId: String,
  ids: Seq[BSONObjectID],
  negate: Boolean
)