package org.ada.web.controllers.dataset

import akka.stream.Materializer
import org.ada.web.models.{LineWidget, Widget}
import org.ada.web.models.Widget.WidgetWrites
import org.ada.server.models._
import org.ada.server.models.ml.classification.Classifier.ClassifierIdentity
import org.ada.server.models.{BasicDisplayOptions, FieldTypeId, FieldTypeSpec}
import org.incal.spark_ml.models.classification.{ClassificationEvalMetric, Classifier}
import org.incal.spark_ml.models.result._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import org.ada.server.services.StatsService
import views.html.{classificationrun => view}

import scala.reflect.runtime.universe.TypeTag

abstract class ClassificationRunControllerImpl[R <: ClassificationResult: Format : TypeTag](implicit materializer: Materializer) extends MLRunControllerImpl[R, Classifier] {

  protected def statsService: StatsService

  override protected val mlMethodName = (x: Classifier) => x.name.getOrElse("N/A")

  private val doubleFieldType = ftf.apply(FieldTypeSpec(FieldTypeId.Double)).asValueOf[Any]
  protected implicit val doubleScatterWidgetWrites = new WidgetWrites[Any](Seq(doubleFieldType, doubleFieldType))

  override protected def showView = { implicit ctx =>
    (view.show(router)(_, _, _, _)).tupled
  }

  override protected def listView = { implicit ctx =>
    (view.list(router)(_, _, _, _, _, _, _, _, _, _, _)).tupled
  }

  protected def binCurvesToWidgets(
    binCurves: Traversable[BinaryClassificationCurves],
    height: Int
  ): Traversable[Widget] = {
    def widget(title: String, xCaption: String, yCaption: String, series: Traversable[Seq[(Double, Double)]]) = {
      if (series.exists(_.nonEmpty)) {
        val data = series.toSeq.zipWithIndex.map { case (data, index) =>
          ("Run " + (index + 1).toString, data)
        }
        val displayOptions = BasicDisplayOptions(gridWidth = Some(12), height = Some(height))
        val widget = LineWidget[Double](
          title, xCaption, yCaption, data, Some(0), Some(1), Some(0), Some(1), displayOptions)
        Some(widget)
        //      ScatterWidget(title, xCaption, yCaption, data, BasicDisplayOptions(gridWidth = Some(6), height = Some(450)))
      } else
        None
    }

    val rocWidget = widget("ROC", "FPR", "TPR", binCurves.map(_.roc))
    val prWidget = widget("PR", "Recall", "Precision", binCurves.map(_.precisionRecall))
    val fMeasureThresholdWidget = widget("FMeasure by Threshold", "Threshold", "F-Measure", binCurves.map(_.fMeasureThreshold))
    val precisionThresholdWidget = widget("Precision by Threshold", "Threshold", "Precision", binCurves.map(_.precisionThreshold))
    val recallThresholdWidget = widget("Recall by Threshold", "Threshold", "Recall", binCurves.map(_.recallThreshold))

    Seq(rocWidget, prWidget, fMeasureThresholdWidget, precisionThresholdWidget, recallThresholdWidget).flatten
  }
}