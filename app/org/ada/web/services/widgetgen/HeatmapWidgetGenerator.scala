package org.ada.web.services.widgetgen

import org.ada.web.models.HeatmapWidget
import org.ada.server.models._
import org.ada.server.calc.CalculatorExecutor
import org.ada.server.calc.impl.{NumericDistributionFlowOptions, NumericDistributionOptions, SeqBinCalcTypePack}
import org.ada.web.util.shorten

private trait HeatmapWidgetGenerator[S <: WidgetSpec, ACCUM, AGG] extends CalculatorWidgetGenerator[S, HeatmapWidget, SeqBinCalcTypePack[ACCUM, AGG]] {

  protected val xFlowMin: Double
  protected val xFlowMax: Double
  protected val yFlowMin: Double
  protected val yFlowMax: Double

  protected def specToBinCounts: S => (Int, Int)

  protected def caption: Seq[String] => String

  protected def aggToDouble: AGG => Option[Double]

  override protected def specToOptions = (spec: S) =>
   Seq(
     NumericDistributionOptions(specToBinCounts(spec)._1),
     NumericDistributionOptions(specToBinCounts(spec)._2)
   )

  override protected def specToFlowOptions = (spec: S) =>
    Seq(
      NumericDistributionFlowOptions(specToBinCounts(spec)._1, xFlowMin, xFlowMax),
      NumericDistributionFlowOptions(specToBinCounts(spec)._2, yFlowMin, yFlowMax)
    )

  override protected def specToSinkOptions = specToFlowOptions

  override protected val supportArray = false

  override def apply(
    spec: S)(
    fieldNameMap: Map[String, Field]
  ) =
    (indecesAggs: SeqBinCalcTypePack[ACCUM, AGG]#OUT) =>
      if (indecesAggs.nonEmpty) {
        def label(fieldName: String) = shorten(fieldNameMap.get(fieldName).get.labelOrElseName, 30)

        val yGroupSize = indecesAggs.map(_._1(1)).toSet.size
        val groupedIndecesAggs = indecesAggs.toSeq.grouped(yGroupSize).toList

        val aggs = groupedIndecesAggs.map(_.map { case (_, agg) => aggToDouble(agg)} )
        val definedAggs = aggs.flatten.flatten

        val yValues = groupedIndecesAggs.head.map(_._1(1).setScale(2, BigDecimal.RoundingMode.FLOOR).toString)
        val xValues = groupedIndecesAggs.map(_.head._1(0).setScale(2, BigDecimal.RoundingMode.FLOOR).toString)


        val fieldNames = spec.fieldNames.toSeq
        val titlex = title(spec).getOrElse(caption(fieldNames.map(label(_))))

        val widget = HeatmapWidget(
          titlex, xValues, yValues, Some(label(fieldNames(0))), Some(label(fieldNames(1))), aggs, Some(definedAggs.min), Some(definedAggs.max), false, spec.displayOptions
        )
        Some(widget)
      } else
        None
}

private class HeatmapAggWidgetGenerator(
    val aggType: AggType.Value,
    val xFlowMin: Double,
    val xFlowMax: Double,
    val yFlowMin: Double,
    val yFlowMax: Double
  ) extends HeatmapWidgetGenerator[HeatmapAggWidgetSpec, Any, Option[Double]] {

  override protected def aggToDouble = identity

  override protected def specToBinCounts =
    (spec: HeatmapAggWidgetSpec) => (spec.xBinCount, spec.yBinCount)

  override protected def caption =
    (fieldLabels: Seq[String]) =>
      s"${fieldLabels(0)} vs. ${fieldLabels(1)} by ${fieldLabels(2)} (${aggType.toString})"

  override protected val seqExecutor = {
    val executor = aggType match {
      case AggType.Mean => seqBinMeanExec
      case AggType.Max => seqBinMaxExec
      case AggType.Min => seqBinMinExec
      case AggType.Variance => seqBinVarianceExec
    }
    executor.asInstanceOf[CalculatorExecutor[SeqBinCalcTypePack[Any, Option[Double]], Seq[Field]]]
  }
}

private class GridDistributionCountWidgetGenerator(
    val xFlowMin: Double,
    val xFlowMax: Double,
    val yFlowMin: Double,
    val yFlowMax: Double
  ) extends HeatmapWidgetGenerator[GridDistributionCountWidgetSpec, Int, Int] {

  override protected def aggToDouble = (count: Int) => Some(count.toDouble)

  override protected def specToBinCounts =
    (spec: GridDistributionCountWidgetSpec) => (spec.xBinCount, spec.yBinCount)

  override protected def caption =
    (fieldLabels: Seq[String]) =>
      s"${fieldLabels(0)} vs. ${fieldLabels(1)} (Count)"

  override protected val seqExecutor = seqBinCountExec
}

object HeatmapAggWidgetGenerator {

  type GEN = CalculatorWidgetGenerator[HeatmapAggWidgetSpec, HeatmapWidget, SeqBinCalcTypePack[Any, Option[Double]]]

  def apply(
    aggType: AggType.Value)(
    xFlowMin: Double,
    xFlowMax: Double,
    yFlowMin: Double,
    yFlowMax: Double
  ): GEN = new HeatmapAggWidgetGenerator(aggType, xFlowMin, xFlowMax, yFlowMin, yFlowMax)


  def apply(
    aggType: AggType.Value,
    xFlowMinMax: (Double, Double),
    yFlowMinMax: (Double, Double)
  ): GEN = apply(aggType)(xFlowMinMax._1, xFlowMinMax._2, yFlowMinMax._1, yFlowMinMax._2)
}

object GridDistributionCountWidgetGenerator {

  type GEN = CalculatorWidgetGenerator[GridDistributionCountWidgetSpec, HeatmapWidget, SeqBinCalcTypePack[Int, Int]]

  def apply(
    xFlowMin: Double,
    xFlowMax: Double,
    yFlowMin: Double,
    yFlowMax: Double
  ): GEN = new GridDistributionCountWidgetGenerator(xFlowMin, xFlowMax, yFlowMin, yFlowMax)

  def apply(
    xFlowMinMax: (Double, Double),
    yFlowMinMax: (Double, Double)
  ): GEN = apply(xFlowMinMax._1, xFlowMinMax._2, yFlowMinMax._1, yFlowMinMax._2)
}