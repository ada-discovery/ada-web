package org.ada.web.runnables.core

import javax.inject.Inject
import org.ada.server.dataaccess.RepoTypes.JsonReadonlyRepo
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.models.{DataView, Field, WidgetGenerationMethod}
import org.ada.web.services.WidgetGenerationService
import org.incal.core.runnables.{InputFutureRunnableExt, RunnableHtmlOutput}
import org.incal.core.util.seqFutures
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BenchmarkWidgetGenerationForMultiDataSets @Inject()(
    val dsaf: DataSetAccessorFactory,
    val wgs: WidgetGenerationService
  ) extends InputFutureRunnableExt[BenchmarkWidgetGenerationForMultiDataSetsSpec] with BenchmarkWidgetGenerationHelper {

  override def runAsFuture(input: BenchmarkWidgetGenerationForMultiDataSetsSpec) =
    seqFutures(input.dataSetIds)(dataSetId =>
      genForDataSet(dataSetId, None, input.repetitions, input.warmUp)
    ).map(_ => ())
}

case class BenchmarkWidgetGenerationForMultiDataSetsSpec(
  dataSetIds: Seq[String],
  repetitions: Int,
  warmUp: Boolean
)

class BenchmarkWidgetGeneration @Inject()(
  val dsaf: DataSetAccessorFactory,
  val wgs: WidgetGenerationService
) extends InputFutureRunnableExt[BenchmarkWidgetGenerationSpec] with BenchmarkWidgetGenerationHelper {

  override def runAsFuture(input: BenchmarkWidgetGenerationSpec) =
    genForDataSet(input.dataSetId, input.viewId, input.repetitions, input.warmUp)
}

case class BenchmarkWidgetGenerationSpec(
  dataSetId: String,
  viewId: Option[BSONObjectID],
  repetitions: Int,
  warmUp: Boolean
)

trait BenchmarkWidgetGenerationHelper extends RunnableHtmlOutput {

  val dsaf: DataSetAccessorFactory
  val wgs: WidgetGenerationService

  private val methods = WidgetGenerationMethod.values.toSeq.sortBy(_.toString)

  def genForDataSet(
    dataSetId: String,
    viewId: Option[BSONObjectID],
    repetitions: Int,
    warmUp: Boolean
  ): Future[Unit] = {
    val dsa = dsaf(dataSetId).get
    val dataSetRepo = dsa.dataSetRepo

    for {
      name <- dsa.dataSetName
      setting <- dsa.setting

      views <- viewId.map(viewId =>
        dsa.dataViewRepo.get(viewId).map(view => Seq(view).flatten)
      ).getOrElse(
        dsa.dataViewRepo.find()
      )

      fields <- dsa.fieldRepo.find()

      // warm-up
      _ <- if (warmUp) dataSetRepo.find() else Future(())

      viewMethodTimes <-
        seqFutures( for { view <- views; method <- methods } yield (view, method) ) { case (view, method) =>
          genWidgets(dataSetRepo, fields, view, method, repetitions).map ( time =>
            (view, method, time)
          )
        }
    } yield
      viewMethodTimes.groupBy(_._1).foreach { case (view, items) =>
        addParagraph(bold(s"$name -> ${view.name} (${setting.storageType}):"))
        addOutput("<hr/>")
        items.map { case (_, method, time) =>
          addParagraph(s"${method.toString}: $time")
        }
        addOutput("<br>")
      }
  }

  private def genWidgets(
    dataSetRepo: JsonReadonlyRepo,
    fields: Traversable[Field],
    view: DataView,
    method: WidgetGenerationMethod.Value,
    repetitions: Int
  ): Future[Long] = {
    val start = new java.util.Date()
    for {
      _ <- seqFutures((1 to repetitions)) { _ =>
        wgs.apply(view.widgetSpecs, dataSetRepo, Nil, Map(), fields, method)
      }
    } yield
      (new java.util.Date().getTime - start.getTime) / repetitions
  }
}