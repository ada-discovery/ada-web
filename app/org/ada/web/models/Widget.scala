package org.ada.web.models

import org.ada.server.dataaccess.AdaConversionException
import org.ada.server.field.FieldType
import org.ada.server.models.DataSetFormattersAndIds.JsObjectIdentity
import org.ada.server.models.{BasicDisplayOptions, ChartType, DisplayOptions, MultiChartDisplayOptions}
import play.api.libs.json._
import reactivemongo.play.json.BSONFormats._
import play.api.libs.functional.syntax._
import org.ada.server.json._
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import org.ada.server.calc.impl.{BasicStatsResult, IndependenceTestResult, Quartiles}
import org.ada.web.controllers.dataset.IndependenceTestResult.independenceTestResultFormat

abstract class Widget {
  val title: String
  val displayOptions: DisplayOptions
  val _id: BSONObjectID = BSONObjectID.generate()
}

case class CategoricalCountWidget(
  title: String,
  fieldName: String,
  fieldLabel: String,
  showLabels: Boolean,
  showLegend: Boolean,
  useRelativeValues: Boolean,
  isCumulative: Boolean,
  data: Seq[(String, Traversable[Count[String]])],
  displayOptions: MultiChartDisplayOptions = MultiChartDisplayOptions()
) extends Widget

case class CategoricalCheckboxCountWidget(
  title: String,
  fieldName: String,
  data: Seq[(Boolean, Count[String])], // Boolean - means checked / unchecked
  displayOptions: DisplayOptions = BasicDisplayOptions()
) extends Widget

case class NumericalCountWidget[T](
  title: String,
  fieldName: String,
  fieldLabel: String,
  useRelativeValues: Boolean,
  isCumulative: Boolean,
  data: Seq[(String, Traversable[Count[T]])],
  displayOptions: MultiChartDisplayOptions = MultiChartDisplayOptions()
) extends Widget

case class ScatterWidget[T1, T2](
  title: String,
  xFieldName: String,
  yFieldName: String,
  xAxisCaption: String,
  yAxisCaption: String,
  data: Seq[(String, Traversable[(T1, T2)])],
  displayOptions: DisplayOptions = BasicDisplayOptions()
) extends Widget

case class ValueScatterWidget[T1, T2, T3](
  title: String,
  xFieldName: String,
  yFieldName: String,
  valueFieldName: String,
  xAxisCaption: String,
  yAxisCaption: String,
  data: Traversable[(T1, T2, T3)],
  displayOptions: DisplayOptions = BasicDisplayOptions()
) extends Widget

case class LineWidget[T](
  title: String,
  xAxisCaption: String,
  yAxisCaption: String,
  data: Seq[(String, Seq[(T,T)])],
  xMin: Option[T] = None,
  xMax: Option[T] = None,
  yMin: Option[T] = None,
  yMax: Option[T] = None,
  displayOptions: DisplayOptions = BasicDisplayOptions()
) extends Widget

case class BoxWidget[T <% Ordered[T]](
  title: String,
  xAxisCaption: Option[String],
  yAxisCaption: String,
  data: Seq[(String, Quartiles[T])],
  min: Option[T] = None,
  max: Option[T] = None,
  displayOptions: DisplayOptions = BasicDisplayOptions()
) extends Widget {
  def ordering = implicitly[Ordering[T]]
}

case class HeatmapWidget(
  title: String,
  xCategories: Seq[String],
  yCategories: Seq[String],
  xAxisCaption: Option[String],
  yAxisCaption: Option[String],
  data: Seq[Seq[Option[Double]]],
  min: Option[Double] = None,
  max: Option[Double] = None,
  twoColors: Boolean = true,
  displayOptions: DisplayOptions = BasicDisplayOptions()
) extends Widget

case class BasicStatsWidget(
  title: String,
  fieldLabel: String,
  data: BasicStatsResult,
  displayOptions: DisplayOptions = BasicDisplayOptions()
) extends Widget

case class IndependenceTestWidget(
  title: String,
  data: Seq[(String, IndependenceTestResult)],
  displayOptions: DisplayOptions = BasicDisplayOptions()
) extends Widget

case class HtmlWidget(
  title: String,
  content: String,
  displayOptions: DisplayOptions = BasicDisplayOptions()
) extends Widget

case class Count[+T](
  value: T,
  count: Int,
  key: Option[String] = None
)

object Widget {

  implicit val chartTypeFormat = EnumFormat(ChartType)
  implicit val basicDisplayOptionsFormat = Json.format[BasicDisplayOptions]
  implicit val multiChartDisplayOptionsFormat = Json.format[MultiChartDisplayOptions]

  implicit val displayOptionsFormat: Format[DisplayOptions] = new SubTypeFormat[DisplayOptions](
    Seq(
      RuntimeClassFormat(basicDisplayOptionsFormat),
      RuntimeClassFormat(multiChartDisplayOptionsFormat)
    )
  )

  implicit val stringCountFormat: Format[Count[String]] = (
    (__ \ "value").format[String] and
    (__ \ "count").format[Int] and
    (__ \ "key").formatNullable[String]
  )(Count[String](_, _, _), {x => (x.value, x.count, x.key)})

  def countFormat[T](fieldType: FieldType[T]): Format[Count[T]] = {
    implicit val valueFormat = FieldTypeFormat.apply[T](fieldType)

    (
      (__ \ "value").format[T] and
      (__ \ "count").format[Int] and
      (__ \ "key").formatNullable[String]
    )(Count[T](_, _, _), {x => (x.value, x.count, x.key)})
  }

  implicit val tupleFormat = TupleFormat[String, Seq[Count[String]]]
  implicit val tuple2Format = TupleFormat[String, String, Seq[Seq[Double]]]
  implicit val optionFormat = new OptionFormat[Double]

  def quartilesFormat[T <% Ordered[T]](fieldType: FieldType[T]): Format[Quartiles[T]] = {
    implicit val valueFormat = FieldTypeFormat.apply[T](fieldType)

    (
      (__ \ "lowerWhisker").format[T] and
      (__ \ "lowerQuantile").format[T] and
      (__ \ "median").format[T] and
      (__ \ "upperQuantile").format[T] and
      (__ \ "upperWhisker").format[T]
    )(Quartiles[T](_, _, _, _, _), { x => (x.lowerWhisker, x.lowerQuantile, x.median, x.upperQuantile, x.upperWhisker)})
  }

  def quartilesWrites[T](fieldType: FieldType[T]): Writes[Quartiles[T]] = {
    implicit val valueFormat = FieldTypeFormat.apply[T](fieldType)

    (
      (__ \ "lowerWhisker").write[T] and
      (__ \ "lowerQuantile").write[T] and
      (__ \ "median").write[T] and
      (__ \ "upperQuantile").write[T] and
      (__ \ "upperWhisker").write[T]
    ){x => (x.lowerWhisker, x.lowerQuantile, x.median, x.upperQuantile, x.upperWhisker)}
  }

  def boxWidgetFormat[T <% Ordered[T]](fieldType: FieldType[T]): Format[BoxWidget[T]] = {
    implicit val valueFormat = FieldTypeFormat.applyOptional[T](fieldType)
    implicit val quartilesFormatx = quartilesFormat[T](fieldType)
    implicit val tupleFormat = TupleFormat[String, Quartiles[T]]

    (
      (__ \ "title").format[String] and
      (__ \ "xAxisCaption").formatNullable[String] and
      (__ \ "yAxisCaption").format[String] and
      (__ \ "data").format[Seq[(String, Quartiles[T])]] and
      (__ \ "min").format[Option[T]] and
      (__ \ "max").format[Option[T]] and
      (__ \ "displayOptions").format[DisplayOptions]
    )(BoxWidget[T](_, _, _, _, _, _, _), {x => (x.title, x.xAxisCaption, x.yAxisCaption, x.data, x.min, x.max, x.displayOptions)})
  }

  def boxWidgetWrites[T](fieldType: FieldType[T]): Writes[BoxWidget[T]] = {
    implicit val valueFormat = FieldTypeFormat.applyOptional[T](fieldType)
    implicit val quartilesWritesx = quartilesWrites[T](fieldType)
    implicit val tupleFormat = TupleWrites[String, Quartiles[T]]

    (
      (__ \ "title").write[String] and
      (__ \ "xAxisCaption").writeNullable[String] and
      (__ \ "yAxisCaption").write[String] and
      (__ \ "data").write[Seq[(String, Quartiles[T])]] and
      (__ \ "min").write[Option[T]] and
      (__ \ "max").write[Option[T]] and
      (__ \ "displayOptions").write[DisplayOptions]
    ){x => (x.title, x.xAxisCaption, x.yAxisCaption, x.data, x.min, x.max, x.displayOptions)}
  }

  def numericalCountWidgetFormat[T](fieldType: FieldType[T]): Format[NumericalCountWidget[T]] = {
    implicit val valueFormat = FieldTypeFormat.apply[T](fieldType)
    implicit val countFormatVal = countFormat[T](fieldType)
    implicit val tupleFormat = TupleFormat[String, Traversable[Count[T]]]

    (
      (__ \ "title").format[String] and
      (__ \ "fieldName").format[String] and
      (__ \ "fieldLabel").format[String] and
      (__ \ "useRelativeValues").format[Boolean] and
      (__ \ "isCumulative").format[Boolean] and
      (__ \ "data").format[Seq[(String, Traversable[Count[T]])]] and
      (__ \ "displayOptions").format[MultiChartDisplayOptions]
    )(NumericalCountWidget[T](_, _, _, _, _, _, _), {x => (x.title, x.fieldName, x.fieldLabel, x.useRelativeValues, x.isCumulative, x.data, x.displayOptions)})
  }

  def lineWidgetFormat[T](fieldType: FieldType[T]): Format[LineWidget[T]] = {
    implicit val valueFormat = FieldTypeFormat.apply[T](fieldType)
    implicit val valueOptionalFormat = FieldTypeFormat.applyOptional[T](fieldType)
    implicit val tuple1Format = TupleFormat[T, T]
    implicit val tuple2Format = TupleFormat[String, Seq[(T, T)]]

    (
      (__ \ "title").format[String] and
      (__ \ "xAxisCaption").format[String] and
      (__ \ "yAxisCaption").format[String] and
      (__ \ "data").format[Seq[(String, Seq[(T,T)])]] and
      (__ \ "xMin").format[Option[T]] and
      (__ \ "xMax").format[Option[T]] and
      (__ \ "yMin").format[Option[T]] and
      (__ \ "yMax").format[Option[T]] and
      (__ \ "displayOptions").format[DisplayOptions]
    )(LineWidget[T](_, _, _, _, _, _, _, _, _), {x => (x.title, x.xAxisCaption, x.yAxisCaption, x.data, x.xMin, x.xMax, x.yMin, x.yMax, x.displayOptions)})
  }

  implicit def scatterWidgetFormat[T1, T2](
    fieldType1: FieldType[T1],
    fieldType2: FieldType[T2]
  ): Format[ScatterWidget[T1, T2]] = {
    implicit val value1Format = FieldTypeFormat.apply[T1](fieldType1)
    implicit val value2Format = FieldTypeFormat.apply[T2](fieldType2)

    implicit val tuple1Format = TupleFormat[T1, T2]
    implicit val tuple2Format = TupleFormat[String, Traversable[(T1, T2)]]

    (
      (__ \ "title").format[String] and
      (__ \ "xFieldName").format[String] and
      (__ \ "yFieldName").format[String] and
      (__ \ "xAxisCaption").format[String] and
      (__ \ "yAxisCaption").format[String] and
      (__ \ "data").format[Seq[(String, Traversable[(T1, T2)])]] and
      (__ \ "displayOptions").format[DisplayOptions]
    )(ScatterWidget[T1, T2](_, _, _, _, _, _, _), {x => (x.title, x.xFieldName, x.yFieldName, x.xAxisCaption, x.yAxisCaption, x.data, x.displayOptions)})
  }

  implicit def valueScatterWidgetFormat[T1, T2, T3](
    fieldType1: FieldType[T1],
    fieldType2: FieldType[T2],
    fieldType3: FieldType[T3]
  ): Format[ValueScatterWidget[T1, T2, T3]] = {
    implicit val value1Format = FieldTypeFormat.apply[T1](fieldType1)
    implicit val value2Format = FieldTypeFormat.apply[T2](fieldType2)
    implicit val value3Format = FieldTypeFormat.apply[T3](fieldType3)

    implicit val tupleFormat = TupleFormat[T1, T2, T3]

    (
      (__ \ "title").format[String] and
      (__ \ "xFieldName").format[String] and
      (__ \ "yFieldName").format[String] and
      (__ \ "valueFieldName").format[String] and
      (__ \ "xAxisCaption").format[String] and
      (__ \ "yAxisCaption").format[String] and
      (__ \ "data").format[Traversable[(T1, T2, T3)]] and
      (__ \ "displayOptions").format[DisplayOptions]
    )(ValueScatterWidget[T1, T2, T3](_, _, _, _, _, _, _, _), {x => (x.title, x.xFieldName, x.yFieldName, x.valueFieldName, x.xAxisCaption, x.yAxisCaption, x.data, x.displayOptions)})
  }

  implicit val heatmapWidgetFormat: Format[HeatmapWidget] = {
    (
      (__ \ "title").format[String] and
      (__ \ "xCategories").format[Seq[String]] and
      (__ \ "yCategories").format[Seq[String]] and
      (__ \ "xAxisCaption").formatNullable[String] and
      (__ \ "yAxisCaption").formatNullable[String] and
      (__ \ "data").format[Seq[Seq[Option[Double]]]] and
      (__ \ "min").format[Option[Double]] and
      (__ \ "max").format[Option[Double]] and
      (__ \ "twoColors").format[Boolean] and
      (__ \ "displayOptions").format[DisplayOptions]
    )(HeatmapWidget(_, _, _, _, _, _, _, _, _, _), {x => (x.title, x.xCategories, x.yCategories, x.xAxisCaption, x.yAxisCaption, x.data, x.min, x.max, x.twoColors, x.displayOptions)})
  }

  implicit val basicStatsResulFormat = Json.format[BasicStatsResult]

  implicit val basicStatsWidgetFormat = Json.format[BasicStatsWidget]

  implicit val stringTestTupleFormat = TupleFormat[String, IndependenceTestResult]

  implicit val independenceTestWidgetFormat = Json.format[IndependenceTestWidget]

  implicit val stringCountTupleFormat = TupleFormat[String, Traversable[Count[String]]]

  implicit val booleanStringCountTupleFormat = TupleFormat[Boolean, Count[String]]

  implicit val categoricalCheckboxWidgetFormat = Json.format[CategoricalCheckboxCountWidget]


  class WidgetWrites[T](
    fieldTypes: Seq[FieldType[T]],
    defaultDoubleFieldType: Option[FieldType[Double]] = None
  ) extends Writes[Widget] {

    private val concreteClassFieldName = "concreteClass"

    override def writes(o: Widget): JsValue = {
      val concreteClassName = o.getClass.getName

      // widget as a json
      val json = o match {
        case e: CategoricalCountWidget =>
          Json.format[CategoricalCountWidget].writes(e)

        case e: NumericalCountWidget[T]  =>
          try {
            numericalCountWidgetFormat(fieldTypes.last).writes(e)
          } catch {
            case ex: AdaConversionException =>
              // if the conversion fails let's try double (if the field type provided)
              defaultDoubleFieldType.map(
                numericalCountWidgetFormat(_).writes(e.asInstanceOf[NumericalCountWidget[Double]])
              ).getOrElse(throw ex)
          }

        case e: LineWidget[T]  =>
          lineWidgetFormat(fieldTypes.head).writes(e)

        case e: ScatterWidget[T, T] =>
          scatterWidgetFormat(fieldTypes(fieldTypes.size - 2), fieldTypes.last).writes(e)

        case e: ValueScatterWidget[T, T, T] =>
          valueScatterWidgetFormat(fieldTypes(0), fieldTypes(1), fieldTypes(2)).writes(e)

        case e: BoxWidget[T] =>
          boxWidgetWrites(fieldTypes.last).writes(e)

        case e: HeatmapWidget =>
          heatmapWidgetFormat.writes(e)

        case e: BasicStatsWidget =>
          basicStatsWidgetFormat.writes(e)

        case e: IndependenceTestWidget =>
          independenceTestWidgetFormat.writes(e)

        case e: HtmlWidget =>
          Json.format[HtmlWidget].writes(e)

        case e: CategoricalCheckboxCountWidget =>
          categoricalCheckboxWidgetFormat.writes(e)
      }

      // field type(s) as json
      val fieldTypeJson = o match {
        case e: ScatterWidget[T, T] =>
          val xFieldType = fieldTypes(fieldTypes.size - 2)
          val yFieldType = fieldTypes.last

          Json.obj(
            "xFieldType" -> JsString(xFieldType.spec.fieldType.toString),
            "yFieldType" -> JsString(yFieldType.spec.fieldType.toString)
          )

        case _ =>
          val json = fieldTypes match {
            case Nil => JsNull
            case _ => JsString(fieldTypes.last.spec.fieldType.toString)
          }

          Json.obj(
            "fieldType" -> json
          )
      }

      json.asInstanceOf[JsObject] ++ fieldTypeJson ++ Json.obj(
        concreteClassFieldName -> JsString(concreteClassName),
        JsObjectIdentity.name -> o._id
      )
    }
  }
}

// TODO: move elsewhere
object CategoricalCountWidget {

  def groupDataByValue(chartSpec: CategoricalCountWidget): Traversable[(String, Seq[Int])] =
    chartSpec.data match {
      case Nil => Nil
      case series =>
        val firstSeriesValueLabels = series.head._2.map(_.value)

        val otherValueLabels = series.tail.map(_._2.map(_.value)).flatten.toSet.toSeq
        val firstSeriesValueLabelsSet = firstSeriesValueLabels.toSet
        val extraValueLabels = otherValueLabels.filterNot(firstSeriesValueLabelsSet)

        (firstSeriesValueLabels ++ extraValueLabels).map { value =>
          val counts = chartSpec.data.map { series =>
            series._2.find(_.value.equals(value)).map(_.count).getOrElse(0)
          }
          (value, counts)
        }
    }
}