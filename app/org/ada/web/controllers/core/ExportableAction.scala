package org.ada.web.controllers.core

import org.ada.server.field.FieldType
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.mvc._
import org.ada.web.util.WebExportUtil._
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Source}
import org.ada.server.models.FieldTypeId
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.Criterion
import org.incal.core.dataaccess.Sort
import org.incal.play.controllers.ReadonlyControllerImpl

import scala.concurrent.Future

trait ExportableAction[E] {

  this: ReadonlyControllerImpl[E, _] =>

  private val preDisplayStreamBuffer = 10

  protected def exportToCsv(
    filename: String,
    delimiter: String = ",",
    eol: String = "\n",
    replacements: Traversable[(String, String)] = Nil,
    escapeStringValues: Boolean = false)(
    fieldNames: Traversable[String],
    orderBy: Option[String] = None,
    filter: Seq[FilterCondition] = Nil,
    extraCriteria: Seq[Criterion[Any]] = Nil,
    useProjection: Boolean = true,
    nameFieldTypeMap: Map[String, FieldType[_]] = Map())(
    implicit materializer: Materializer
  ) = Action.async { implicit request =>
    val projection = if (useProjection) fieldNames else Nil

    for {
      jsonStream <- getJsonStream(filter, extraCriteria, orderBy, projection)
    } yield {
      val finalJsonStream =
        if (nameFieldTypeMap.nonEmpty)
          toDisplayJsonsStream(jsonStream, nameFieldTypeMap, escapeStringValues)
        else
          if (escapeStringValues) escapeStrings(jsonStream) else jsonStream

      jsonStreamToCsvFile(finalJsonStream, fieldNames, filename, delimiter, eol, replacements)
    }
  }

  protected def exportToJson(
    filename: String)(
    orderBy: Option[String],
    filter: Seq[FilterCondition] = Nil,
    extraCriteria: Seq[Criterion[Any]] = Nil,
    fieldNames: Traversable[String] = Nil,
    useProjection: Boolean = true,
    nameFieldTypeMap: Map[String, FieldType[_]] = Map())(
    implicit ev: Format[E], materializer: Materializer
  ) = Action.async { implicit request =>
    val projection = if (useProjection) fieldNames else Nil

    for {
      jsonStream <- getJsonStream(filter, extraCriteria, orderBy, projection)
    } yield {
      val finalJsonStream =
        if (nameFieldTypeMap.nonEmpty)
          toDisplayJsonsStream(jsonStream, nameFieldTypeMap)
        else
          jsonStream

      jsonStreamToJsonFile(finalJsonStream, filename)
    }
  }

  private def getJsonStream(
    filter: Seq[FilterCondition] = Nil,
    extraCriteria: Seq[Criterion[Any]] = Nil,
    orderBy: Option[String] = None,
    projection: Traversable[String] = Nil)(
    implicit materializer: Materializer
  ): Future[Source[JsObject, _]] =
    for {
      criteria <- toCriteria(filter)

      recordsSource <- repo.findAsStream(
        criteria = criteria ++ extraCriteria,
        sort = orderBy.fold(Seq[Sort]())(toSort),
        projection = projection
      )
    } yield
      recordsSource.map(item => toJson(item).as[JsObject])

  private def toDisplayJsonsStream(
    source: Source[JsObject, _],
    nameFieldTypeMap: Map[String, FieldType[_]],
    escapeStringValues: Boolean = false
  ): Source[JsObject, _] = {
    val nameIsCategoricalMap = nameFieldTypeMap.map { case (fieldName, fieldType) =>
      val fieldTypeId = fieldType.spec.fieldType
      (fieldName, fieldTypeId == FieldTypeId.String || fieldTypeId == FieldTypeId.Enum || fieldTypeId == FieldTypeId.Boolean)
    }

    val toDisplayJson = toDisplayJsonAux(nameFieldTypeMap, nameIsCategoricalMap, escapeStringValues)(_)
    source.async.buffer(preDisplayStreamBuffer, OverflowStrategy.backpressure).map(toDisplayJson) // mapAsync(2)(toDisplayJson)
  }

  private def toDisplayJsonAux(
    nameFieldTypeMap: Map[String, FieldType[_]],
    nameIsCategoricalMap: Map[String, Boolean],
    escapeStringValues: Boolean = false)(
    json: JsObject
  ): JsObject = {
    val displayJsonFields = json.fields.map { case (fieldName, jsValue) =>
      val isCategorical = nameIsCategoricalMap.get(fieldName).getOrElse(false)

      val displayJson = jsValue match {
        case JsNull => JsNull
        case _ =>
          nameFieldTypeMap.get(fieldName).map { fieldType =>
            val stringValue = fieldType.jsonToDisplayString(jsValue)

            if (isCategorical && escapeStringValues)
              JsString("\"" + stringValue + "\"")
            else
              JsString(stringValue)
          }.getOrElse(
            jsValue // if not recognized (e.g. _id)
          )
      }

      (fieldName, displayJson)
    }
    JsObject(displayJsonFields)
  }

  private def escapeStrings(
    source: Source[JsObject, _]
  ): Source[JsObject, _] =
    source.map { item =>
      val newFields = item.fields.map { case (fieldName, jsValue) =>

        val newJsValue = jsValue match {
          case JsString(value) => JsString("\"" + value + "\"")
          case _ => jsValue
        }

        (fieldName, newJsValue)
      }

      JsObject(newFields)
    }
}