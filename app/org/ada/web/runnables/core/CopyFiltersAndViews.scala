package org.ada.web.runnables.core

import javax.inject.Inject
import org.ada.server.AdaException
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.incal.core.runnables.InputFutureRunnableExt
import org.ada.server.models.Filter.FilterIdentity
import org.ada.server.models.DataView.DataViewIdentity
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

      // clear ids and save the filters
      _ <- {
        val clearedFilters = filters.map(FilterIdentity.clear)
        targetDsa.filterRepo.save(clearedFilters)
      }

      // clear ids and save the views
      _ <- {
        val clearedViews = views.map(DataViewIdentity.clear)
        targetDsa.dataViewRepo.save(clearedViews)
      }
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