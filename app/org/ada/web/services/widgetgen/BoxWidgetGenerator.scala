package org.ada.web.services.widgetgen

import org.ada.server.models._
import org.ada.web.models.BoxWidget
import org.ada.server.calc.impl.QuartilesCalcNoOptionsTypePack

object BoxWidgetGenerator extends CalculatorWidgetGenerator[BoxWidgetSpec, BoxWidget[Any], QuartilesCalcNoOptionsTypePack[Any]]
  with NoOptionsCalculatorWidgetGenerator[BoxWidgetSpec] {

  override protected val seqExecutor = quartilesAnySeqExec

  override protected val supportArray = true

  override protected def extraStreamCriteria(
    spec: BoxWidgetSpec,
    fields: Seq[Field]
  ) = withNotNull(fields)

  override def apply(
    spec: BoxWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (result: QuartilesCalcNoOptionsTypePack[Any]#OUT) =>
      result.map { quartiles =>
        implicit val ordering = quartiles.ordering
        val field = fieldNameMap.get(spec.fieldName).get
        val chartTitle = title(spec).getOrElse(field.labelOrElseName)
        BoxWidget[Any](chartTitle, None, field.labelOrElseName, Seq(("", quartiles)), None, None, spec.displayOptions)
      }
}