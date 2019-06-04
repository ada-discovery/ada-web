package org.ada.web.runnables.core

import javax.inject.Inject
import org.incal.core.runnables.{InputFutureRunnable, InputFutureRunnableExt, RunnableHtmlOutput}
import org.incal.core.util.seqFutures
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.Logger
import org.ada.server.dataaccess.JsonReadonlyRepoExtra._
import org.ada.server.models.DataSpaceMetaInfo
import org.ada.server.AdaException
import reactivemongo.bson.BSONObjectID
import org.ada.web.services.DataSpaceService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CountSanityCheckForDataSpaceRecursively @Inject() (
    val dsaf: DataSetAccessorFactory,
    dataSpaceService: DataSpaceService
  ) extends InputFutureRunnableExt[CountSanityCheckForDataSpaceRecursivelySpec] with CountSanityCheckHelper {

  override def runAsFuture(input: CountSanityCheckForDataSpaceRecursivelySpec) =
    for {
      dataSpaces <- dataSpaceService.allAsTree

      dataSpace = dataSpaces.map(dataSpaceService.findRecursively(input.dataSpaceId, _)).find(_.isDefined).flatten

      results <- checkDataSpaceRecursively(dataSpace.getOrElse(
        throw new AdaException(s"Data space ${input.dataSpaceId} not found.")
      ))
    } yield {
      val filteredCounts = results.filter { case (_, count1, count2) => count1 != count2 }
      if (filteredCounts.isEmpty) {
        addParagraphAndLog(s"Data space ${input.dataSpaceId.stringify} passed a sanity count check.")
      } else {
        addParagraphAndLog(s"Found ${filteredCounts.size} (out of ${results.size}) data sets with inconsistent counts:")
        filteredCounts.foreach { case (dataSetId, count1, count2) =>
          addParagraphAndLog(s"Data set $dataSetId has an inconsistent count $count1 vs $count2 (# ids).")
        }
      }
    }

  private def checkDataSpaceRecursively(
    dataSpace: DataSpaceMetaInfo
  ): Future[Traversable[(String, Int, Int)]] = {
    val dataSetIds = dataSpace.dataSetMetaInfos.map(_.id)

    for {
      results <- seqFutures(dataSetIds)(checkDataSet)
      subResults <- seqFutures(dataSpace.children)(checkDataSpaceRecursively)
    } yield
      results ++ subResults.flatten
  }
}

class CountSanityCheckForDataSet @Inject() (
  val dsaf: DataSetAccessorFactory
) extends InputFutureRunnableExt[CountSanityCheckForDataSetSpec] with CountSanityCheckHelper {

  override def runAsFuture(
    input: CountSanityCheckForDataSetSpec
  ) =
    for {
      (_, count1, count2) <- checkDataSet(input.dataSetId)
    } yield
      if (count1 != count2) {
        addParagraphAndLog(s"Data set '${input.dataSetId}' has an inconsistent count $count1 vs $count2 (# ids).")
      } else {
        addParagraphAndLog(s"Data set ${input.dataSetId} passed a sanity count check.")
      }
}

trait CountSanityCheckHelper extends RunnableHtmlOutput {

  protected val dsaf: DataSetAccessorFactory

  protected val logger = Logger

  protected def checkDataSet(
    dataSetId: String
  ): Future[(String, Int, Int)] = {
    logger.info(s"Checking the count for the data set $dataSetId.")

    val dsa = dsaf(dataSetId).get

    for {
      count <- dsa.dataSetRepo.count()
      ids <- dsa.dataSetRepo.allIds
    } yield
      (dataSetId, count, ids.size)
  }

  protected def addParagraphAndLog(message: String) = {
    logger.info(message)
    addParagraph(message)
  }
}

case class CountSanityCheckForDataSpaceRecursivelySpec(
  dataSpaceId: BSONObjectID
)

case class CountSanityCheckForDataSetSpec(
  dataSetId: String
)