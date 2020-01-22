package org.ada.web

import org.ada.server.dataaccess.JsonUtil
import org.ada.server.models._
import org.ada.web.models._
import org.incal.core.util.toHumanReadableCamel
import org.incal.core.{ConditionType, FilterCondition}
import play.api.libs.json.{Json, Writes}
import play.twirl.api.Html

import scala.collection.Traversable

package object util {

  def shorten(string : String, length: Int = 25) =
    if (string.length > length) string.substring(0, length - 2) + "..." else string


  def fieldLabel(fieldName : String): String =
    toHumanReadableCamel(JsonUtil.unescapeKey(fieldName))

  def fieldLabel(fieldName : String, fieldLabelMap : Option[Map[String, String]]) = {
    val defaultLabel = toHumanReadableCamel(JsonUtil.unescapeKey(fieldName))
    fieldLabelMap.map(_.getOrElse(fieldName, defaultLabel)).getOrElse(defaultLabel)
  }

  def fieldLabel(field: Field): String =
    field.label.getOrElse(fieldLabel(field.name))

  def conditionTypeToReadableOperator(conditionType: ConditionType.Value) =
    conditionType match {
      case ConditionType.Equals => "="
      case ConditionType.NotEquals => "≠"
      case ConditionType.Greater => ">"
      case ConditionType.GreaterEqual => "≥"
      case ConditionType.Less => "<"
      case ConditionType.LessEqual => "≤"
      case ConditionType.In => "in"
      case ConditionType.NotIn => "not in"
      case ConditionType.RegexEquals => "like"
      case ConditionType.RegexNotEquals => "not like"
    }

  val operators = Vector(
    ConditionType.Equals -> 1,
    ConditionType.NotEquals -> 2,
    ConditionType.Greater -> 3,
    ConditionType.GreaterEqual -> 4,
    ConditionType.Less -> 5,
    ConditionType.LessEqual -> 6,
    ConditionType.In  -> 7,
    ConditionType.NotIn -> 8,
    ConditionType.RegexEquals -> 9,
    ConditionType.RegexNotEquals -> 10
  ).sortBy(_._2).map(x => x._1 -> conditionTypeToReadableOperator(x._1))

  def valueOrUndefined(condition: FilterCondition, trim: Boolean = false) = {
    val valueOrLabel =
      condition.valueLabel match {
        case Some(label) => Some(label)
        case None => condition.value
      }

    valueOrLabel match {
      case None => "undefined"
      case Some(s) if s.isEmpty => "undefined"
      case Some(s) if trim => shorten(s, 80)
      case Some(s) => s
    }
  }

  def widgetElementId(chart: Widget) = chart._id.stringify + "Widget"

  // retyping of column items needed because play templates do not support generics
  def typeColumn[T](column: (Option[String], String, T => Any)): (Option[String], String, Any  => Html) =
    (column._1, column._2, {item: Any =>
      val value = column._3(item.asInstanceOf[T])
      if (value.isInstanceOf[Html])
        value.asInstanceOf[Html]
      else if (value == null)
        Html("")
      else
        Html(value.toString)
    })

  def typeColumns[T](
    columns: (Option[String], String, T => Any)*
  ): Traversable[(Option[String], String, Any => Html)] =
    columns.map(typeColumn[T])

  def formatScheduleTime(scheduledTime: ScheduledTime) = {
    val wildcard = "&nbsp;*&nbsp;"

    val formatTimeElement = { i: Option[Int] =>
      i.map { value =>
        if (value < 10) "0" + value else value.toString
      }.getOrElse(
        wildcard
      )
    }

    val weekDay = scheduledTime.weekDay.map(_.toString.take(3) + "&nbsp;").getOrElse("&nbsp;&nbsp;&nbsp;&nbsp;")
    weekDay + Seq(scheduledTime.hour, scheduledTime.minute, scheduledTime.second).map(formatTimeElement).mkString(":")
  }

  def enumToValueString(enum: Enumeration): Seq[(String, String)] =
    enum.values.toSeq.sortBy(_.id).map(value => (value.toString, toHumanReadableCamel(value.toString)))

  def toChartData(widget: CategoricalCountWidget) =
    widget.data.map { case (name, series) =>
      val sum = if (widget.isCumulative) series.map(_.count).max else series.map(_.count).sum
      val data = series.map { case Count(label, count, key) =>
        (shorten(label), if (widget.useRelativeValues) 100 * count.toDouble / sum else count, key)
      }
      (name, data)
    }

  def toChartData(widget: NumericalCountWidget[_]) = {
    def numericValue(x: Any) =
      x match {
        case x: java.util.Date => x.getTime.toString
        case _ => x.toString
      }

    widget.data.map { case (name, series) =>
      val sum = if (widget.isCumulative) series.map(_.count).max else series.map(_.count).sum
      val data = series.map { case Count(value, count, _) =>
        (numericValue(value), if (widget.useRelativeValues) 100 * count.toDouble / sum else count)
      }
      (name, data)
    }
  }

  def toChartData(widget: ScatterWidget[_, _]) = {
    def numericValue(x: Any) =
      x match {
        case x: java.util.Date => x.getTime.toString
        case _ => x.toString
      }

    widget.data.map { case (name, points) =>
      var numPoints = points.map { case (point1, point2) => (numericValue(point1), numericValue(point2)) }
      (name, numPoints)
    }
  }

  def toChartData(widget: ValueScatterWidget[_, _, _]) = {
    def numericValue(x: Any) =
      x match {
        case x: java.util.Date => x.getTime.toString
        case _ => x.toString
      }

    widget.data.map { case (x, y, z) =>
      (numericValue(x), numericValue(y), numericValue(z))
    }
  }

  def toJsonHtml[T](o: T)(implicit tjs: Writes[T]): Html =
    Html(Json.stringify(Json.toJson(o)))
}