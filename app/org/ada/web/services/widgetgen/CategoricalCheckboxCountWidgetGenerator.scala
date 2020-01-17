package org.ada.web.services.widgetgen

import org.ada.server.calc.impl.UniqueDistributionCountsCalc.UniqueDistributionCountsCalcTypePack
import org.ada.server.field.FieldTypeHelper
import org.ada.server.models._
import org.ada.web.models.{CategoricalCheckboxCountWidget, Count}
import org.incal.core.dataaccess._
import spire.ClassTag

private class CategoricalCheckboxCountWidgetGenerator(criteria: Seq[Criterion[Any]]) extends CalculatorWidgetGenerator[CategoricalCheckboxWidgetSpec, CategoricalCheckboxCountWidget, UniqueDistributionCountsCalcTypePack[Any]]
  with DistributionWidgetGeneratorHelper
  with NoOptionsCalculatorWidgetGenerator[CategoricalCheckboxWidgetSpec] {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  private val booleanType = ftf(FieldTypeSpec(FieldTypeId.Boolean)).asValueOf[Boolean]

  override protected val seqExecutor = uniqueDistributionCountsSeqExec[Any]

  override protected val supportArray = true

  override def apply(
    spec: CategoricalCheckboxWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (uniqueCounts: UniqueDistributionCountsCalcTypePack[Any]#OUT) => {
      val field = fieldNameMap.get(spec.fieldName).get
      val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]

      // only enum and boolean types are expected
      val allZeroCounts =
        field.fieldType match {
          case FieldTypeId.Enum =>
            field.enumValues.map { case (key, label) => Count(label, 0, Some(key)) }

          case FieldTypeId.Boolean =>
            def boolZeroCount(value: Boolean) = Count(booleanType.valueToDisplayString(Some(value)), 0, Some(value.toString))
            Seq(boolZeroCount(true), boolZeroCount(false))

          case _ => Nil
        }

      if  (allZeroCounts.nonEmpty) {
        val nonZeroCounts = createStringCounts(uniqueCounts, fieldType)
        val nonZeroCountValues = nonZeroCounts.map(_.value).toSet
        val zeroCounts = allZeroCounts.filterNot(count => nonZeroCountValues.contains(count.value))

        val allCounts = nonZeroCounts ++ zeroCounts

        val fieldCriteria = criteria.filter(_.fieldName == spec.fieldName)

        def findCheckedValues[E: ClassTag](fun: E => Seq[String]) =
          fieldCriteria.collect { case x: E => x }.headOption.map(fun).getOrElse(Nil).toSet

        val inValues = findCheckedValues[InCriterion[Any]] {
          _.value.map { value =>
            fieldType.valueToDisplayString(Some(value))
          }
        }

        val equalsValues = findCheckedValues[EqualsCriterion[Any]] { criterion =>
          Seq(fieldType.valueToDisplayString(Some(criterion.value)))
        }

        val notInValues = findCheckedValues[NotInCriterion[Any]] {
          _.value.map { value =>
            fieldType.valueToDisplayString(Some(value))
          }
        }

        val notEqualsValues = findCheckedValues[NotEqualsCriterion[Any]] { criterion =>
          Seq(fieldType.valueToDisplayString(Some(criterion.value)))
        }

        val allValues = allZeroCounts.map(_.value).toSet

        val checkedValues = intersect(inValues, equalsValues)
        val checkedValues2 = allValues.diff(notInValues.union(notEqualsValues))
        val finalCheckedValues = intersect(checkedValues, checkedValues2)

        val checkedCounts = allCounts.map { count =>
          val checked = finalCheckedValues.contains(count.value)

          (checked, count)
        }

        Some(CategoricalCheckboxCountWidget(field.labelOrElseName, spec.fieldName, checkedCounts.toSeq.sortBy(_._2.value), spec.displayOptions))
      } else
        None
    }

  private def intersect(
    set1: Set[String],
    set2: Set[String]
  ) =
    if (set1.nonEmpty && set2.nonEmpty)
      set1.intersect(set2)
    else if (set1.nonEmpty)
      set1
    else
      set2
}

object CategoricalCheckboxCountWidgetGenerator {
  type Gen = CalculatorWidgetGenerator[CategoricalCheckboxWidgetSpec, CategoricalCheckboxCountWidget, UniqueDistributionCountsCalcTypePack[Any]]

  def apply(criteria: Seq[Criterion[Any]]): Gen = new CategoricalCheckboxCountWidgetGenerator(criteria)
}
