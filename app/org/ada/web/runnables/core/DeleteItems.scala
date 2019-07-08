package org.ada.web.runnables.core

import scala.concurrent.ExecutionContext.Implicits.global
import runnables.DsaInputFutureRunnable
import org.incal.core.runnables.RunnableHtmlOutput
import reactivemongo.bson.BSONObjectID

class DeleteItems extends DsaInputFutureRunnable[DeleteItemsSpec] with RunnableHtmlOutput {

  override def runAsFuture(input: DeleteItemsSpec) = {
    val dsa = createDsa(input.dataSetId)

    for {
      _ <- if (input.ids.size == 1) dsa.dataSetRepo.delete(input.ids.head) else dsa.dataSetRepo.delete(input.ids)
    } yield
      addParagraph(s"Deleted <b>${input.ids.size}</b> items:<br/>")
  }
}

case class DeleteItemsSpec(
  dataSetId: String,
  ids: Seq[BSONObjectID]
)