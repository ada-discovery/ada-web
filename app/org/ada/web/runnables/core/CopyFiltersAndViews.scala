package org.ada.web.runnables.core

import java.util.Date

import javax.inject.Inject
import org.ada.server.AdaException
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.models._
import org.incal.core.runnables.InputFutureRunnableExt

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Copies filters and views from one data set to another (specified in <code>CopyFiltersAndViewsSpec</code>).
  *
  * Note that compatibility of data sets is checked simply by comparing the field names,
  * i.e. the target data set must contain all the field from the source one.
  *
  * @param dsaf The Guice-injected data set accessor factory.
  * @since 2019
  */
// TODO: move to ada-server
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

        require(
          sourceDiff.isEmpty,
          s"Source and target fields are not compatible. These source fields do not appear in the target data set: ${sourceDiff.mkString(", ")}}."
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

        val newWidgetSpecs = view.widgetSpecs.map { widget =>
          val newSubFilterId = widget.subFilterId.map(oldNewFilterIdMap.get(_).get)
          widget match {
            case x: DistributionWidgetSpec => x.copy(subFilterId = newSubFilterId)
            case x: CumulativeCountWidgetSpec => x.copy(subFilterId = newSubFilterId)
            case x: BoxWidgetSpec => x.copy(subFilterId = newSubFilterId)
            case x: ScatterWidgetSpec => x.copy(subFilterId = newSubFilterId)
            case x: ValueScatterWidgetSpec => x.copy(subFilterId = newSubFilterId)
            case x: HeatmapAggWidgetSpec => x.copy(subFilterId = newSubFilterId)
            case x: GridDistributionCountWidgetSpec => x.copy(subFilterId = newSubFilterId)
            case x: CorrelationWidgetSpec => x.copy(subFilterId = newSubFilterId)
            case x: IndependenceTestWidgetSpec => x.copy(subFilterId = newSubFilterId)
            case x: BasicStatsWidgetSpec => x.copy(subFilterId = newSubFilterId)
            case x: CustomHtmlWidgetSpec => x.copy(subFilterId = newSubFilterId)
            case _ => widget
          }
        }

        view.copy(_id = None, timeCreated = new Date(), filterOrIds = newFilterOrIds, widgetSpecs = newWidgetSpecs)
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