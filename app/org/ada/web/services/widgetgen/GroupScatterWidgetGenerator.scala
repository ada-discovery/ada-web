package org.ada.web.services.widgetgen

import org.incal.core.dataaccess.Criterion
import org.ada.server.models.{Field, ScatterWidgetSpec}
import org.ada.web.models.ScatterWidget
import org.ada.server.calc.impl.GroupTupleCalcTypePack
import org.ada.web.util.{fieldLabel, shorten}

import scala.reflect.runtime.universe._

private class GroupScatterWidgetGenerator[T1, T2](
    implicit inputTypeTag: TypeTag[GroupTupleCalcTypePack[String, T1, T2]#IN]
  ) extends CalculatorWidgetGenerator[ScatterWidgetSpec, ScatterWidget[T1, T2], GroupTupleCalcTypePack[String, T1, T2]]
    with NoOptionsCalculatorWidgetGenerator[ScatterWidgetSpec] {

//  override protected val seqExecutor = groupTupleSeqExec[String, T1, T2]

  override protected val seqExecutor = groupUniqueTupleSeqExec[String, T1, T2]

  override protected val supportArray = false

  override protected def extraStreamCriteria(
    spec: ScatterWidgetSpec,
    fields: Seq[Field]
  ) = withNotNull(fields.tail)

  override def apply(
    spec: ScatterWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (data: GroupTupleCalcTypePack[String, T1, T2]#OUT) =>
      if (data.nonEmpty) {
        val xField = fieldNameMap.get(spec.xFieldName).get
        val yField = fieldNameMap.get(spec.yFieldName).get
        val groupField = fieldNameMap.get(spec.groupFieldName.get).get

        val shortXFieldLabel = shorten(xField.labelOrElseName, 20)
        val shortYFieldLabel = shorten(yField.labelOrElseName, 20)
        val shortGroupFieldLabel = shorten(groupField.labelOrElseName, 20)
        val finalData = data.map { case (groupName, values) => (groupName.getOrElse("Undefined"), values)}.toSeq.sortBy(_._1)

        val widget = ScatterWidget(
          title(spec).getOrElse(s"$shortXFieldLabel vs. $shortYFieldLabel by $shortGroupFieldLabel"),
          xField.name,
          yField.name,
          xField.labelOrElseName,
          yField.labelOrElseName,
          finalData,
          spec.displayOptions
        )
        Some(widget)
      } else
        None
}

object GroupScatterWidgetGenerator {
  def apply[T1, T2](
    implicit inputTypeTag: TypeTag[GroupTupleCalcTypePack[String, T1, T2]#IN]
  ): CalculatorWidgetGenerator[ScatterWidgetSpec, ScatterWidget[T1, T2], GroupTupleCalcTypePack[String, T1, T2]] =
    new GroupScatterWidgetGenerator[T1, T2]
}