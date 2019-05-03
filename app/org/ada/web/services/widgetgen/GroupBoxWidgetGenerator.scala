package org.ada.web.services.widgetgen

import org.incal.core.dataaccess.Criterion
import org.ada.server.field.FieldTypeHelper
import org.ada.web.models.BoxWidget
import org.ada.server.models.{BoxWidgetSpec, Field}
import org.ada.server.calc.impl.GroupQuartilesCalcNoOptionsTypePack

object GroupBoxWidgetGenerator extends CalculatorWidgetGenerator[BoxWidgetSpec, BoxWidget[Any], GroupQuartilesCalcNoOptionsTypePack[Any, Any]]
  with NoOptionsCalculatorWidgetGenerator[BoxWidgetSpec] {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override protected val seqExecutor = groupQuartilesAnySeqExec[Any]

  override protected val supportArray = true

  override protected def extraStreamCriteria(
    spec: BoxWidgetSpec,
    fields: Seq[Field]
  ) = withNotNull(fields.tail)

  override def apply(
    spec: BoxWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (data: GroupQuartilesCalcNoOptionsTypePack[Any, Any]#OUT) => {
      val definedData = data.collect { case (group, Some(quartiles)) => (group, quartiles) }
      if (definedData.nonEmpty) {
        implicit val ordering = definedData.head._2.ordering

        val field = fieldNameMap.get(spec.fieldName).get
        val groupField = fieldNameMap.get(spec.groupFieldName.get).get

        val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]

        val chartTitle = title(spec).getOrElse(field.labelOrElseName)

        val finalData = definedData.toSeq.sortBy(_._1.isEmpty).map { case (group, quartiles) =>
          val groupString = group match {
            case Some(group) => groupFieldType.valueToDisplayString(Some(group))
            case None => "Undefined"
          }
          (groupString, quartiles)
        }.sortBy(_._1)

        val widget = BoxWidget[Any](chartTitle, Some(groupField.labelOrElseName), field.labelOrElseName, finalData, None, None, spec.displayOptions)
        Some(widget)
      } else
        None
    }
}