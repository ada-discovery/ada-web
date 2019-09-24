package org.ada.web.services.widgetgen

import org.ada.web.models.{CategoricalCountWidget, Count, NumericalCountWidget}
import org.ada.server.field.{FieldType, FieldTypeHelper}
import org.ada.server.models._
import org.ada.server.models.DistributionWidgetSpec
import org.ada.server.calc.impl.UniqueDistributionCountsCalc.UniqueDistributionCountsCalcTypePack
import org.ada.server.calc.impl._
import org.ada.web.util.{fieldLabel, shorten}
import org.ada.server.field.FieldUtil._

object CategoricalDistributionWidgetGenerator extends CalculatorWidgetGenerator[DistributionWidgetSpec, CategoricalCountWidget, UniqueDistributionCountsCalcTypePack[Any]]
  with DistributionWidgetGeneratorHelper
  with NoOptionsCalculatorWidgetGenerator[DistributionWidgetSpec] {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override protected val seqExecutor = uniqueDistributionCountsSeqExec[Any]

  override protected val supportArray = true

  override def apply(
    spec: DistributionWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (uniqueCounts: UniqueDistributionCountsCalcTypePack[Any]#OUT) => {
      val field = fieldNameMap.get(spec.fieldName).get
      val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]
      val stringCounts = createStringCounts(uniqueCounts, fieldType)

      createCategoricalWidget(spec, field, None)(Seq(("All", stringCounts)))
    }
}

private class NumericDistributionWidgetGenerator(
    flowMin: Double,
    flowMax: Double
  ) extends CalculatorWidgetGenerator[DistributionWidgetSpec, NumericalCountWidget[Any], NumericDistributionCountsCalcTypePack]
    with DistributionWidgetGeneratorHelper {

  override protected val seqExecutor = numericDistributionCountsSeqExec

  override protected def specToOptions = (spec: DistributionWidgetSpec) =>
    NumericDistributionOptions(spec.numericBinCount.getOrElse(defaultNumericBinCount))

  override protected def specToFlowOptions = (spec: DistributionWidgetSpec) =>
    NumericDistributionFlowOptions(spec.numericBinCount.getOrElse(defaultNumericBinCount), flowMin, flowMax)

  override protected def specToSinkOptions = specToFlowOptions

  override protected val supportArray = true

  override protected def extraStreamCriteria(
    spec: DistributionWidgetSpec,
    fields: Seq[Field]
  ) = withNotNull(fields)

  override def apply(
    spec: DistributionWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (counts: NumericDistributionCountsCalcTypePack#OUT) => {
      val field = fieldNameMap.get(spec.fieldName).get
      val finalCounts = createNumericCounts(counts, convertNumeric(field.fieldType))

      createNumericWidget(spec, field, None)(Seq(("All", finalCounts)))
    }
}

object NumericDistributionWidgetGenerator {

  type GEN = CalculatorWidgetGenerator[DistributionWidgetSpec, NumericalCountWidget[Any], NumericDistributionCountsCalcTypePack]

  def apply(
    flowMin: Double,
    flowMax: Double
  ): GEN = new NumericDistributionWidgetGenerator(flowMin, flowMax)

  def apply(
    flowMinMax: (Double, Double)
  ): GEN = apply(flowMinMax._1, flowMinMax._2)
}

object UniqueIntDistributionWidgetGenerator extends CalculatorWidgetGenerator[DistributionWidgetSpec, NumericalCountWidget[Any], UniqueDistributionCountsCalcTypePack[Long]]
  with DistributionWidgetGeneratorHelper
  with NoOptionsCalculatorWidgetGenerator[DistributionWidgetSpec] {

  override protected val seqExecutor = uniqueDistributionCountsSeqExec[Long]

  override protected val supportArray = true

  override protected def extraStreamCriteria(
    spec: DistributionWidgetSpec,
    fields: Seq[Field]
  ) = withNotNull(fields)

  override def apply(
    spec: DistributionWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (longUniqueCounts: UniqueDistributionCountsCalcTypePack[Long]#OUT) => {
      val field = fieldNameMap.get(spec.fieldName).get
      val finalCounts = prepareIntCounts(longUniqueCounts)
      createNumericWidget(spec, field, None)(Seq(("All", finalCounts)))
    }
}

object GroupCategoricalDistributionWidgetGenerator extends CalculatorWidgetGenerator[DistributionWidgetSpec, CategoricalCountWidget, GroupUniqueDistributionCountsCalcTypePack[Any, Any]]
  with DistributionWidgetGeneratorHelper
  with NoOptionsCalculatorWidgetGenerator[DistributionWidgetSpec] {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override protected val seqExecutor = groupUniqueDistributionCountsSeqExec[Any, Any]

  override protected val supportArray = true

  override def apply(
    spec: DistributionWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (groupCounts: GroupUniqueDistributionCountsCalcTypePack[Any, Any]#OUT) => {
      val field = fieldNameMap.get(spec.fieldName).get
      val groupField = fieldNameMap.get(spec.groupFieldName.get).get
      val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]
      val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]
      val groupStringCounts = createGroupStringCounts(groupCounts, groupFieldType, fieldType)

      createCategoricalWidget(spec, field, Some(groupField))(groupStringCounts)
    }
}

private class GroupNumericDistributionWidgetGenerator(
    flowMin: Double,
    flowMax: Double
  ) extends CalculatorWidgetGenerator[DistributionWidgetSpec, NumericalCountWidget[Any], GroupNumericDistributionCountsCalcTypePack[Any]]
    with DistributionWidgetGeneratorHelper {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override protected val seqExecutor = groupNumericDistributionCountsSeqExec[Any]

  override protected def specToOptions = (spec: DistributionWidgetSpec) =>
    NumericDistributionOptions(spec.numericBinCount.getOrElse(defaultNumericBinCount))

  override protected def specToFlowOptions = (spec: DistributionWidgetSpec) =>
    NumericDistributionFlowOptions(spec.numericBinCount.getOrElse(defaultNumericBinCount), flowMin, flowMax)

  override protected def specToSinkOptions = specToFlowOptions

  override protected val supportArray = true

  override protected def extraStreamCriteria(
    spec: DistributionWidgetSpec,
    fields: Seq[Field]
  ) = withNotNull(fields.tail)

  override def apply(
    spec: DistributionWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (groupCounts: GroupNumericDistributionCountsCalcTypePack[Any]#OUT) => {
      val field = fieldNameMap.get(spec.fieldName).get
      val groupField = fieldNameMap.get(spec.groupFieldName.get).get
      val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]
      val finalCounts = createGroupNumericCounts(groupCounts, groupFieldType, field)

      createNumericWidget(spec, field, Some(groupField))(finalCounts)
    }
}

object GroupNumericDistributionWidgetGenerator {

  type GEN = CalculatorWidgetGenerator[DistributionWidgetSpec, NumericalCountWidget[Any], GroupNumericDistributionCountsCalcTypePack[Any]]

  def apply(
    flowMin: Double,
    flowMax: Double
  ): GEN = new GroupNumericDistributionWidgetGenerator(flowMin, flowMax)

  def apply(
    flowMinMax: (Double, Double)
  ): GEN = apply(flowMinMax._1, flowMinMax._2)
}

object GroupUniqueIntDistributionWidgetGenerator extends CalculatorWidgetGenerator[DistributionWidgetSpec, NumericalCountWidget[Any], GroupUniqueDistributionCountsCalcTypePack[Any, Long]]
  with DistributionWidgetGeneratorHelper
  with NoOptionsCalculatorWidgetGenerator[DistributionWidgetSpec] {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override protected val seqExecutor = groupUniqueDistributionCountsSeqExec[Any, Long]

  override protected val supportArray = true

  override protected def extraStreamCriteria(
    spec: DistributionWidgetSpec,
    fields: Seq[Field]
  ) = withNotNull(fields.tail)

  override def apply(
    spec: DistributionWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (longGroupCounts: GroupUniqueDistributionCountsCalcTypePack[Any, Long]#OUT) => {
      val field = fieldNameMap.get(spec.fieldName).get
      val groupField = fieldNameMap.get(spec.groupFieldName.get).get
      val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]

      val finalCounts = toGroupStringValues(longGroupCounts, groupFieldType).map {
        case (groupString, valueCounts) =>
          (groupString, prepareIntCounts(valueCounts))
      }
      createNumericWidget(spec, field, Some(groupField))(finalCounts)
    }
}

trait DistributionWidgetGeneratorHelper {

  protected val defaultNumericBinCount = 20
  protected val maxIntCountsForZeroPadding = 1000

  protected def createWidget(
    spec: DistributionWidgetSpec,
    field: Field,
    groupField: Option[Field]
  ) = {
    val widgetGen = if (field.isNumeric) (createCategoricalWidget)_ else (createNumericWidget)_
    widgetGen(spec, field, groupField)
  }

  protected def createCategoricalWidget(
    spec: DistributionWidgetSpec,
    field: Field,
    groupField: Option[Field]
  ) = (countSeries:  Traversable[(String, Traversable[Count[Any]])]) => {
    val nonZeroCountExists = countSeries.exists(_._2.exists(_.count > 0))
    if (nonZeroCountExists) {
      Some(createCategoricalWidgetAux(spec, field, groupField)(countSeries))
    } else
      Option.empty[CategoricalCountWidget]
  }

  protected def createNumericWidget(
    spec: DistributionWidgetSpec,
    field: Field,
    groupField: Option[Field]
  ) = (countSeries:  Traversable[(String, Traversable[Count[Any]])]) => {
    val nonZeroCountExists = countSeries.exists(_._2.exists(_.count > 0))
    if (nonZeroCountExists) {
      Some(createNumericWidgetAux(spec, field, groupField)(countSeries))
    } else
      Option.empty[NumericalCountWidget[Any]]
  }

  private def createCategoricalWidgetAux(
    spec: DistributionWidgetSpec,
    field: Field,
    groupField: Option[Field]
  ) = (countSeries:  Traversable[(String, Traversable[Count[Any]])]) => {
    val nonZeroCountSeriesSorted = sortCountSeries(spec.numericBinCount)(countSeries)

    val displayOptions = spec.displayOptions
    val title = displayOptions.title.getOrElse(createTitle(field, groupField))
    val initializedDisplayOptions = displayOptions.copy(chartType = Some(displayOptions.chartType.getOrElse(ChartType.Column)))

    // create a categorical widget
    CategoricalCountWidget(
      title,
      field.name,
      field.labelOrElseName,
      false,
      true,
      spec.relativeValues,
      false,
      nonZeroCountSeriesSorted,
      initializedDisplayOptions
    )
  }

  protected def sortCountSeries(
    binCount: Option[Int]
  ) = (countSeries:  Traversable[(String, Traversable[Count[Any]])]) => {
    // enforce the same categories in all the series
    val labelGroupedCounts = countSeries.flatMap(_._2).groupBy(_.value)
    val nonZeroLabelSumCounts = labelGroupedCounts.map { case (label, counts) =>
      (label, counts.map(_.count).sum)
    }.filter(_._2 > 0)

    val sortedLabels: Seq[String] = nonZeroLabelSumCounts.toSeq.sortBy(_._2).map(_._1.toString)

    val topSortedLabels  = binCount match {
      case Some(maxCategoricalBinCount) => sortedLabels.takeRight(maxCategoricalBinCount)
      case None => sortedLabels
    }

    val countSeriesSorted = countSeries.map { case (seriesName, counts) =>

      val labelCountMap = counts.map { count =>
        val label = count.value.toString
        (label, Count(label, count.count, count.key))
      }.toMap

      val newCounts = topSortedLabels.map ( label =>
        labelCountMap.get(label).getOrElse(Count(label, 0, None))
      )
      (seriesName, newCounts)
    }

    countSeriesSorted.filter(_._2.exists(_.count > 0)).toSeq
  }

  private def createNumericWidgetAux(
    spec: DistributionWidgetSpec,
    field: Field,
    groupField: Option[Field]
  ) = (countSeries:  Traversable[(String, Traversable[Count[Any]])]) => {
    val displayOptions = spec.displayOptions
    val title = displayOptions.title.getOrElse(createTitle(field, groupField))

    val nonZeroNumCountSeries = countSeries.filter(_._2.nonEmpty).toSeq
    val initializedDisplayOptions = displayOptions.copy(chartType = Some(displayOptions.chartType.getOrElse(ChartType.Line)))

    // create a numeric widget
    NumericalCountWidget(
      title,
      field.name,
      field.labelOrElseName,
      spec.relativeValues,
      false,
      nonZeroNumCountSeries,
      initializedDisplayOptions
    )
  }

  protected def createStringCounts[T](
    counts: Traversable[(Option[T], Int)],
    fieldType: FieldType[T]
  ): Traversable[Count[String]] =
    counts.map { case (value, count) =>
      val stringKey = value.map(_.toString)

      val label = value.map{ value =>
        if (fieldType.spec.isArray)
          fieldType.asValueOf[Array[Option[T]]].valueToDisplayString(Some(Array(Some(value))))
        else
          fieldType.valueToDisplayString(Some(value))
      }.getOrElse("Undefined")
      Count(label, count, stringKey)
    }

  protected def createNumericCounts(
    counts: Traversable[(BigDecimal, Int)],
    convert: Option[BigDecimal => Any] = None
  ): Seq[Count[_]] =
    counts.toSeq.sortBy(_._1).map { case (xValue, count) =>
      val convertedValue = convert.map(_.apply(xValue)).getOrElse(xValue.toDouble)
      Count(convertedValue, count, None)
    }

  protected def convertNumeric(fieldType: FieldTypeId.Value) =
    fieldType match {
      case FieldTypeId.Date =>
        val convert = {ms: BigDecimal => new java.util.Date(ms.setScale(0, BigDecimal.RoundingMode.CEILING).toLongExact)}
        Some(convert)
      case _ => None
    }

  protected def createGroupStringCounts[G, T](
    groupCounts: Traversable[(Option[G], Traversable[(Option[T], Int)])],
    groupFieldType: FieldType[G],
    fieldType: FieldType[T]
  ): Seq[(String, Traversable[Count[String]])] =
    toGroupStringValues(groupCounts, groupFieldType).map { case (groupString, counts) =>
      (groupString, createStringCounts(counts, fieldType))
    }

  protected def createGroupNumericCounts[G](
    groupCounts: Traversable[(Option[G], Traversable[(BigDecimal, Int)])],
    groupFieldType: FieldType[G],
    field: Field
  ): Seq[(String, Traversable[Count[_]])] = {
    // value converter
    val convert = convertNumeric(field.fieldType)

    // handle group string names and convert values
    toGroupStringValues(groupCounts, groupFieldType).map { case (groupString, counts) =>
      (groupString, createNumericCounts(counts, convert))
    }
  }

  protected def toGroupStringValues[G, T](
    groupCounts: Traversable[(Option[G], Traversable[T])],
    groupFieldType: FieldType[G]
  ): Seq[(String, Traversable[T])] =
    groupCounts.toSeq.sortBy(_._1.isEmpty).map { case (group, values) =>
      val groupString = group match {
        case Some(group) => groupFieldType.valueToDisplayString(Some(group))
        case None => "Undefined"
      }
      (groupString, values)
    }

  protected def prepareIntCounts(
    longUniqueCounts: UniqueDistributionCountsCalcTypePack[Long]#OUT
  ) = {
    val counts = longUniqueCounts.collect{ case (Some(value), count) => Count(value, count)}.toSeq.sortBy(_.value)
    if (counts.nonEmpty) {
      val size = counts.size

      val min = counts.head.value
      val max = counts.last.value

      // if the difference between max and min is "small" enough we can add a zero count for missing values
      if (Math.abs(max - min) < maxIntCountsForZeroPadding) {
        val countsWithZeroes = for (i <- 0 to size - 1) yield {
          val count = counts(i)
          if (i + 1 < size) {
            val nextCount = counts(i + 1)

            val zeroCounts =
              for (missingValue <- count.value + 1 to nextCount.value - 1) yield Count(missingValue, 0)

            Seq(count) ++ zeroCounts
          } else
            Seq(count)
        }

        countsWithZeroes.flatten
      } else
        counts
    } else
      Nil
  }

  protected def createTitle(
    field: Field,
    groupField: Option[Field]
  ): String =
    groupField match {
      case Some(groupField) =>
        shorten(fieldLabel(field), 25) + " by " + shorten(fieldLabel(groupField), 25)

      case None =>
        fieldLabel(field)
    }
}