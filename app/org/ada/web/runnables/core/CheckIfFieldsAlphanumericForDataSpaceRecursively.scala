package org.ada.web.runnables.core

import javax.inject.Inject
import org.ada.server.AdaException
import org.ada.server.dataaccess.RepoTypes.DataSpaceMetaInfoRepo
import org.ada.server.dataaccess.dataset.FieldRepoFactory
import org.ada.server.models.DataSpaceMetaInfo
import org.ada.web.services.DataSpaceService
import org.incal.core.runnables.{InputFutureRunnableExt, RunnableHtmlOutput}
import org.incal.core.util.{hasNonAlphanumericUnderscore, seqFutures}
import play.api.Logger
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CheckIfFieldsAlphanumericForDataSpaceRecursively @Inject() (
  val fieldRepoFactory: FieldRepoFactory,
  dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
  dataSpaceService: DataSpaceService
) extends InputFutureRunnableExt[CheckIfFieldsAlphanumericForDataSpaceRecursivelySpec] with CheckIfFieldsAlphanumericHelper{

  override def runAsFuture(input: CheckIfFieldsAlphanumericForDataSpaceRecursivelySpec) =
    for {
      rootDataSpaces <- dataSpaceService.allAsTree

      dataSpace = rootDataSpaces.map(dataSpaceService.findRecursively(input.dataSpaceId, _)).find(_.isDefined).flatten

      results <- checkDataSpaceRecursively(dataSpace.getOrElse(
        throw new AdaException(s"Data space ${input.dataSpaceId} not found.")
      ))
    } yield {
      val filteredResults = results.filter(_._2.nonEmpty)
      addParagraphAndLog(s"Found ${filteredResults.size} (out of ${results.size}) data sets with wrongly named fields:")

      filteredResults.foreach { case (dataSetId, fieldNames) =>
        val fieldNamesString = if (fieldNames.size > 3) fieldNames.take(3).mkString(", ") + "..." else fieldNames.mkString(", ")
        addParagraphAndLog(s"Data set $dataSetId contains ${fieldNames.size} non-alpha fields: ${fieldNamesString}")
      }
    }

  private def checkDataSpaceRecursively(
    dataSpace: DataSpaceMetaInfo
  ): Future[Traversable[(String, Traversable[String])]] = {
    val dataSetIds = dataSpace.dataSetMetaInfos.map(_.id)

    for {
      results <- seqFutures(dataSetIds)(checkDataSet)
      subResults <- seqFutures(dataSpace.children)(checkDataSpaceRecursively)
    } yield
      results ++ subResults.flatten
  }
}

case class CheckIfFieldsAlphanumericForDataSpaceRecursivelySpec(
  dataSpaceId: BSONObjectID
)

trait CheckIfFieldsAlphanumericHelper extends RunnableHtmlOutput {

  protected val logger = Logger
  protected val escapedDotString = "u002e"
  val fieldRepoFactory: FieldRepoFactory

  protected def addParagraphAndLog(message: String) = {
    logger.info(message)
    addParagraph(message)
  }

  protected def checkDataSet(
    dataSetId: String
  ): Future[(String, Traversable[String])] = {
    logger.info(s"Checking the fields of the data set $dataSetId.")

    val fieldRepo = fieldRepoFactory(dataSetId)

    for {
      fields <- fieldRepo.find()
    } yield {
      val wrongFieldNames = fields.map(_.name).filter { name =>
        hasNonAlphanumericUnderscore(name) || name.contains(escapedDotString)
      }
      (dataSetId, wrongFieldNames)
    }
  }
}