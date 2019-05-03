package org.ada.web.services.widgetgen

import org.ada.web.models.{Count, NumericalCountWidget}
import org.incal.core.dataaccess.Criterion
import org.ada.server.field.{FieldType, FieldTypeHelper}
import org.ada.server.models._
import org.ada.server.calc.impl.UniqueDistributionCountsCalc.UniqueDistributionCountsCalcTypePack
import org.ada.server.calc.impl.{GroupCumulativeOrderedCountsCalcTypePack, _}
import org.ada.web.util.{fieldLabel, shorten}

object CumulativeCountWidgetGenerator extends CalculatorWidgetGenerator[CumulativeCountWidgetSpec, NumericalCountWidget[Any], CumulativeOrderedCountsCalcTypePack[Any]]
  with CumulativeCountWidgetGeneratorHelper
  with NoOptionsCalculatorWidgetGenerator[CumulativeCountWidgetSpec] {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override protected val seqExecutor = cumulativeOrderedCountsAnySeqExec

  override protected val supportArray = true

  override protected def extraStreamCriteria(
    spec: CumulativeCountWidgetSpec,
    fields: Seq[Field]
  ) = withNotNull(fields)

  override def apply(
    spec: CumulativeCountWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (valueCounts: CumulativeOrderedCountsCalcTypePack[Any]#OUT) => {
      val field = fieldNameMap.get(spec.fieldName).get
      val counts = field.fieldType match {
        // numeric
        case FieldTypeId.Double | FieldTypeId.Integer | FieldTypeId.Date =>
          valueCounts.map { case (value, count) => Count(value, count) }

        // string
        case _ =>
          val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]
          createStringCountsDefined(valueCounts, fieldType)
      }

      createWidget(spec, field, None)(Seq(("All", counts)))
    }
}

object GroupCumulativeCountWidgetGenerator extends CalculatorWidgetGenerator[CumulativeCountWidgetSpec, NumericalCountWidget[Any], GroupCumulativeOrderedCountsCalcTypePack[Any, Any]]
  with CumulativeCountWidgetGeneratorHelper
  with NoOptionsCalculatorWidgetGenerator[CumulativeCountWidgetSpec] {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override protected val seqExecutor = groupCumulativeOrderedCountsAnySeqExec[Any]

  override protected val supportArray = true

  override protected def extraStreamCriteria(
    spec: CumulativeCountWidgetSpec,
    fields: Seq[Field]
  ) = withNotNull(fields.tail)

  override def apply(
    spec: CumulativeCountWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (groupCounts:  GroupCumulativeOrderedCountsCalcTypePack[Any, Any]#OUT) => {
      val field = fieldNameMap.get(spec.fieldName).get
      val groupField = fieldNameMap.get(spec.groupFieldName.get).get

      val stringGroupCounts = field.fieldType match {
        // group numeric
        case FieldTypeId.Double | FieldTypeId.Integer | FieldTypeId.Date =>
          val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]

          toGroupStringValues(groupCounts, groupFieldType).map { case (groupString, valueCounts) =>
            val counts = valueCounts.map { case (value, count) => Count(value, count)}
            (groupString, counts)
          }

        // group string
        case _ =>
          val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]
          val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]

          toGroupStringValues(groupCounts, groupFieldType).map { case (groupString, valueCounts) =>
            (groupString, createStringCountsDefined(valueCounts, fieldType))
          }
      }

      // create a widget
      createWidget(spec, field, Some(groupField))(stringGroupCounts)
    }
}

private class CumulativeNumericBinCountWidgetGenerator(
    flowMin: Double,
    flowMax: Double
  ) extends CalculatorWidgetGenerator[CumulativeCountWidgetSpec, NumericalCountWidget[Any], CumulativeNumericBinCountsCalcTypePack]
    with CumulativeCountWidgetGeneratorHelper {

  override protected val seqExecutor = cumulativeNumericBinCountsSeqExec

  override protected def specToOptions = (spec: CumulativeCountWidgetSpec) =>
    NumericDistributionOptions(spec.numericBinCount.getOrElse(defaultNumericBinCount))

  override protected def specToFlowOptions = (spec: CumulativeCountWidgetSpec) =>
    NumericDistributionFlowOptions(spec.numericBinCount.getOrElse(defaultNumericBinCount), flowMin, flowMax)

  override protected def specToSinkOptions = specToFlowOptions

  override protected val supportArray = true

  override protected def extraStreamCriteria(
    spec: CumulativeCountWidgetSpec,
    fields: Seq[Field]
  ) = withNotNull(fields)

  override def apply(
    spec: CumulativeCountWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (valueCounts: CumulativeNumericBinCountsCalcTypePack#OUT) => {
      val field = fieldNameMap.get(spec.fieldName).get

      val counts = createNumericCounts(valueCounts, convertNumeric(field.fieldType))
      createWidget(spec, field, None)(Seq(("All", counts)))
    }
}

object CumulativeNumericBinCountWidgetGenerator {

  type GEN = CalculatorWidgetGenerator[CumulativeCountWidgetSpec, NumericalCountWidget[Any], CumulativeNumericBinCountsCalcTypePack]

  def apply(
    flowMin: Double,
    flowMax: Double
  ): GEN = new CumulativeNumericBinCountWidgetGenerator(flowMin, flowMax)

  def apply(
    flowMinMax: (Double, Double)
  ): GEN = apply(flowMinMax._1, flowMinMax._2)
}

private class GroupCumulativeNumericBinCountWidgetGenerator(
    flowMin: Double,
    flowMax: Double
  ) extends CalculatorWidgetGenerator[CumulativeCountWidgetSpec, NumericalCountWidget[Any], GroupCumulativeNumericBinCountsCalcTypePack[Any]]
    with CumulativeCountWidgetGeneratorHelper {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override protected val seqExecutor = groupCumulativeNumericBinCountsSeqExec[Any]

  override protected def specToOptions = (spec: CumulativeCountWidgetSpec) =>
    NumericDistributionOptions(spec.numericBinCount.getOrElse(defaultNumericBinCount))

  override protected def specToFlowOptions = (spec: CumulativeCountWidgetSpec) =>
    NumericDistributionFlowOptions(spec.numericBinCount.getOrElse(defaultNumericBinCount), flowMin, flowMax)

  override protected def specToSinkOptions = specToFlowOptions

  override protected val supportArray = true

  override protected def extraStreamCriteria(
    spec: CumulativeCountWidgetSpec,
    fields: Seq[Field]
  ) = withNotNull(fields.tail)

  override def apply(
    spec: CumulativeCountWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (valueCounts: GroupCumulativeNumericBinCountsCalcTypePack[Any]#OUT) => {
      val field = fieldNameMap.get(spec.fieldName).get
      val groupField = fieldNameMap.get(spec.groupFieldName.get).get
      val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]

      val counts = createGroupNumericCounts(valueCounts, groupFieldType, field)
      createWidget(spec, field, Some(groupField))(counts)
    }

  private def createGroupNumericCounts[G](
    groupCounts: GroupNumericDistributionCountsCalcTypePack[G]#OUT,
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
}

object GroupCumulativeNumericBinCountWidgetGenerator {

  type GEN = CalculatorWidgetGenerator[CumulativeCountWidgetSpec, NumericalCountWidget[Any], GroupCumulativeNumericBinCountsCalcTypePack[Any]]

  def apply(
    flowMin: Double,
    flowMax: Double
  ): GEN = new GroupCumulativeNumericBinCountWidgetGenerator(flowMin, flowMax)

  def apply(
    flowMinMax: (Double, Double)
  ): GEN = apply(flowMinMax._1, flowMinMax._2)
}

object UniqueCumulativeCountWidgetGenerator extends WidgetGenerator[CumulativeCountWidgetSpec, NumericalCountWidget[Any]] with CumulativeCountWidgetGeneratorHelper {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override type IN = UniqueDistributionCountsCalcTypePack[Any]#OUT

  override def apply(
    spec: CumulativeCountWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) = (counts: IN) => {
    val field = fieldNameMap.get(spec.fieldName).get
    val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]
    val finalCounts = Seq(("All", createStringCounts(counts, fieldType)))
    createWidget(spec, field, None)(toCumCounts(finalCounts))
  }
}


object GroupUniqueCumulativeCountWidgetGenerator extends WidgetGenerator[CumulativeCountWidgetSpec, NumericalCountWidget[Any]] with CumulativeCountWidgetGeneratorHelper {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override type IN = GroupUniqueDistributionCountsCalcTypePack[Any, Any]#OUT

  override def apply(
    spec: CumulativeCountWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) = (groupCounts: IN) => {
    val field = fieldNameMap.get(spec.fieldName).get
    val groupField = fieldNameMap.get(spec.groupFieldName.get).get
    val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]
    val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]

    val finalCounts = createGroupStringCounts(groupCounts, groupFieldType, fieldType)
    createWidget(spec, field, Some(groupField))(toCumCounts(finalCounts))
  }

  private def createGroupStringCounts[G, T](
    groupCounts: Traversable[(Option[G], Traversable[(Option[T], Int)])],
    groupFieldType: FieldType[G],
    fieldType: FieldType[T]
  ): Seq[(String, Traversable[Count[String]])] =
    toGroupStringValues(groupCounts, groupFieldType).map { case (groupString, counts) =>
      (groupString, createStringCounts(counts, fieldType))
    }
}

trait CumulativeCountWidgetGeneratorHelper {

  protected val defaultNumericBinCount = 20

  protected def createWidget(
    spec: CumulativeCountWidgetSpec,
    field: Field,
    groupField: Option[Field]
  ) =
    (countSeries:  Traversable[(String, Traversable[Count[Any]])]) => {
      val displayOptions = spec.displayOptions
      val title = displayOptions.title.getOrElse(createTitle(field, groupField))

      val nonZeroCountSeries = countSeries.filter(_._2.exists(_.count > 0))
      if (nonZeroCountSeries.nonEmpty) {
        val initializedDisplayOptions = displayOptions.copy(chartType = Some(displayOptions.chartType.getOrElse(ChartType.Line)))
        val widget = NumericalCountWidget(title, field.name, field.labelOrElseName, spec.relativeValues, true, nonZeroCountSeries.toSeq, initializedDisplayOptions)
        Some(widget)
      } else
        None
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

  protected def createStringCounts[T](
    counts: Traversable[(Option[T], Int)],
    fieldType: FieldType[T]
  ): Traversable[Count[String]] =
    counts.map { case (value, count) =>
      val stringKey = value.map(_.toString)
      val label = value.map(value => fieldType.valueToDisplayString(Some(value))).getOrElse("Undefined")
      Count(label, count, stringKey)
    }

  protected def createStringCountsDefined[T](
    counts: Traversable[(T, Int)],
    fieldType: FieldType[T]
  ): Traversable[Count[String]] =
    counts.map { case (value, count) =>
      val stringKey = value.toString
      val label = fieldType.valueToDisplayString(Some(value))
      Count(label, count, Some(stringKey))
    }

  protected def createNumericCounts(
    counts: NumericDistributionCountsCalcTypePack#OUT,
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

  // function that converts dist counts to cumulative counts by applying simple running sum
  protected def toCumCounts[T](
    distCountsSeries: Traversable[(String, Traversable[Count[T]])]
  ): Traversable[(String, Seq[Count[T]])] =
    distCountsSeries.map { case (seriesName, distCounts) =>
      val distCountsSeq = distCounts.toSeq
      val cumCounts = distCountsSeq.scanLeft(0) { case (sum, count) =>
        sum + count.count
      }
      val labeledDistCounts: Seq[Count[T]] = distCountsSeq.map(_.value).zip(cumCounts.tail).map { case (value, count) =>
        Count(value, count)
      }
      (seriesName, labeledDistCounts)
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