package org.ada.web.services.widgetgen

import org.ada.web.models.HeatmapWidget
import org.ada.server.models._
import org.ada.server.calc.impl.MatthewsBinaryClassCorrelationCalcTypePack

private class MatthewsCorrelationWidgetGenerator(flowParallelism: Option[Int]) extends CalculatorWidgetGenerator[CorrelationWidgetSpec, HeatmapWidget, MatthewsBinaryClassCorrelationCalcTypePack] {

  override protected val seqExecutor = matthewsBinaryClassCorrelationExec

  override protected def specToOptions = _ => ()

  override protected def specToFlowOptions = _ => flowParallelism

  override protected def specToSinkOptions = _ => flowParallelism

  override protected val supportArray = false

  override def apply(
    spec: CorrelationWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (correlations: MatthewsBinaryClassCorrelationCalcTypePack#OUT) =>
      if (correlations.nonEmpty) {
        val fields = spec.fieldNames.flatMap(fieldNameMap.get)
        val fieldLabels = fields.map(_.labelOrElseName)

        val widget = HeatmapWidget(
          title(spec).getOrElse("Matthews Correlations"), fieldLabels, fieldLabels, None, None, correlations, Some(-1), Some(1), true, spec.displayOptions
        )
        Some(widget)
      } else
        None
}

object MatthewsCorrelationWidgetGenerator {

  def apply(
    flowParallelism: Option[Int]
  ): CalculatorWidgetGenerator[CorrelationWidgetSpec, HeatmapWidget, MatthewsBinaryClassCorrelationCalcTypePack] =
    new MatthewsCorrelationWidgetGenerator(flowParallelism)
}