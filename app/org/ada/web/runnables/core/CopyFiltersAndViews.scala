package org.ada.web.runnables.core

import java.util.Date
import javax.inject.Inject
import org.ada.server.AdaException
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.incal.core.runnables.InputFutureRunnableExt
import scala.concurrent.ExecutionContext.Implicits.global

class CopyFiltersAndViews @Inject()(dsaf: DataSetAccessorFactory) extends InputFutureRunnableExt[CopyFiltersAndViewsSpec] {

  override def runAsFuture(input: CopyFiltersAndViewsSpec) = {
    val sourceDsa = dsaSafe(input.sourceDataSetId)
    val targetDsa = dsaSafe(input.targetDataSetId)

    for {
      // get source fields
      sourceFields <- sourceDsa.fieldRepo.find()

      // get target fields
      targetFields <- targetDsa.fieldRepo.find()

      // check if compatible
      _ = {
        val sourceFieldNames = sourceFields.map(_.name).toSet
        val targetFieldNames = targetFields.map(_.name).toSet

        val sourceDiff = sourceFieldNames.diff(targetFieldNames)
        val targetDiff = targetFieldNames.diff(sourceFieldNames)

        require(
          sourceDiff.isEmpty && targetDiff.isEmpty,
          s"Source and target fields are not compatible: ${sourceDiff.mkString(", ")}, ${targetDiff.mkString(", ")}."
        )
      }

      // get filters
      filters <- sourceDsa.filterRepo.find()

      // get views
      views <- sourceDsa.dataViewRepo.find()

      // clear id and date and save filters
      newFilterIds <- targetDsa.filterRepo.save(
        filters.map(_.copy(_id = None, timeCreated = Some(new Date())))
      )

      // old -> new filter id map
      oldNewFilterIdMap = filters.toSeq.map(_._id.get).zip(newFilterIds.toSeq).toMap

      // clear id and date and replace filter ids
      viewsToSave = views.map { view =>

        val newFilterOrIds = view.filterOrIds.map(
          _ match {
            case Left(conditions) => Left(conditions)
            case Right(filterId) => Right(oldNewFilterIdMap.get(filterId).get)
          }
        )

        view.copy(_id = None, timeCreated = new Date(), filterOrIds = newFilterOrIds)
      }

      // save the views
      _ <- targetDsa.dataViewRepo.save(viewsToSave)
    } yield
      ()
  }

  protected def dsaSafe(dataSetId: String) =
    dsaf(dataSetId).getOrElse(
      throw new AdaException(s"Data set id ${dataSetId} not found.")
    )
}

case class CopyFiltersAndViewsSpec(
  sourceDataSetId: String,
  targetDataSetId: String
)