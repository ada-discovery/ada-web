package org.ada.web.services.widgetgen

import org.ada.web.models.ValueScatterWidget
import org.ada.server.models._
import org.ada.server.calc.impl.Tuple3CalcTypePack
import org.ada.web.util.shorten

import scala.reflect.runtime.universe._

private class ValueScatterWidgetGenerator[T1, T2, T3](
    implicit inputTypeTag: TypeTag[Tuple3CalcTypePack[T1, T2, T3]#IN]
  ) extends CalculatorWidgetGenerator[ValueScatterWidgetSpec, ValueScatterWidget[T1, T2, T3], Tuple3CalcTypePack[T1, T2, T3]]
    with NoOptionsCalculatorWidgetGenerator[ValueScatterWidgetSpec] {

//  override protected val seqExecutor = tuple3SeqExec[T1, T2, T3]

  override protected val seqExecutor = uniqueTuple3SeqExec[T1, T2, T3]

  override protected val supportArray = false

  override protected def extraStreamCriteria(
    spec: ValueScatterWidgetSpec,
    fields: Seq[Field]
  ) = withNotNull(fields)

  override def apply(
    spec: ValueScatterWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (data: Tuple3CalcTypePack[T1, T2, T3]#OUT) =>
      if (data.nonEmpty) {
        val xField = fieldNameMap.get(spec.xFieldName).get
        val yField = fieldNameMap.get(spec.yFieldName).get
        val valueField = fieldNameMap.get(spec.valueFieldName).get

        val shortXFieldLabel = shorten(xField.labelOrElseName, 20)
        val shortYFieldLabel = shorten(yField.labelOrElseName, 20)
        val shortValueFieldLabel = shorten(valueField.labelOrElseName, 20)

        val widget = ValueScatterWidget(
          title(spec).getOrElse(s"$shortXFieldLabel vs. $shortYFieldLabel by $shortValueFieldLabel"),
          xField.name,
          yField.name,
          valueField.name,
          xField.labelOrElseName,
          yField.labelOrElseName,
          data,
          spec.displayOptions
        )
        Some(widget)
      } else
        None
}

object ValueScatterWidgetGenerator {
  def apply[T1, T2, T3](
    implicit inputTypeTag: TypeTag[Tuple3CalcTypePack[T1, T2, T3]#IN]
  ): CalculatorWidgetGenerator[ValueScatterWidgetSpec, ValueScatterWidget[T1, T2, T3], Tuple3CalcTypePack[T1, T2, T3]] =
    new ValueScatterWidgetGenerator[T1, T2, T3]
}

