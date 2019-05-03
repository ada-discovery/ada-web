package org.ada.web.services.widgetgen

import org.ada.web.models.BasicStatsWidget
import org.ada.server.models._
import org.ada.server.calc.impl.BasicStatsCalcTypePack

object BasicStatsWidgetGenerator extends CalculatorWidgetGenerator[BasicStatsWidgetSpec, BasicStatsWidget, BasicStatsCalcTypePack]
  with NoOptionsCalculatorWidgetGenerator[BasicStatsWidgetSpec] {

  override protected val seqExecutor = basicStatsSeqExec

  override protected val supportArray = true

  override def apply(
    spec: BasicStatsWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (results:  BasicStatsCalcTypePack#OUT) =>
      results.map { results =>
        val field = fieldNameMap.get(spec.fieldName).get
        val chartTitle = title(spec).getOrElse(field.labelOrElseName)
        BasicStatsWidget(chartTitle, field.labelOrElseName, results, spec.displayOptions)
      }
}