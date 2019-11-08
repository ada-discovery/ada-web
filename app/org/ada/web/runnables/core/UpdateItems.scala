package org.ada.web.runnables.core

import scala.concurrent.ExecutionContext.Implicits.global
import runnables.DsaInputFutureRunnable
import org.incal.core.runnables.RunnableHtmlOutput
import reactivemongo.bson.BSONObjectID
import play.api.libs.json._

class UpdateItems extends DsaInputFutureRunnable[UpdateItemsSpec] with RunnableHtmlOutput with IdCriterionHelper {

  override def runAsFuture(input: UpdateItemsSpec) = {
    val dsa = createDsa(input.dataSetId)

    for {
      jsons <- dsa.dataSetRepo.find(Seq(criterion(input.ids, input.negate)))

      newJsons = jsons.map(json => json.+((input.stringFieldName, JsString(input.value))))
      _ <- if (newJsons.size == 1) dsa.dataSetRepo.update(newJsons.head) else dsa.dataSetRepo.update(newJsons)
    } yield
      addParagraph(s"Updated <b>${newJsons.size}</b> items:<br/>")
  }
}

case class UpdateItemsSpec(
  dataSetId: String,
  ids: Seq[BSONObjectID],
  negate: Boolean,
  stringFieldName: String,
  value: String
)