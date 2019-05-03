package org.ada.web.controllers

import com.fasterxml.jackson.core.JsonParseException
import org.ada.server.models.Filter._
import org.ada.web.controllers.FilterConditionExtraFormats.eitherFilterOrIdFormat
import org.ada.server.models.{AggType, CorrelationType, FieldTypeId, Filter}
import org.ada.server.models.ml._
import org.ada.server.models.DataSetFormattersAndIds.enumTypeFormat
import org.ada.server.models.ml.classification.ClassificationResult.{standardClassificationRunSpecFormat, temporalClassificationRunSpecFormat}
import org.ada.server.models.ml.regression.RegressionResult.{standardRegressionRunSpecFormat, temporalRegressionRunSpecFormat}
import org.incal.core.FilterCondition
import org.incal.play.PageOrder
import org.incal.spark_ml.models.classification.ClassificationEvalMetric
import org.incal.spark_ml.models.regression.RegressionEvalMetric
import org.incal.spark_ml.models.setting._
import org.incal.spark_ml.models.VectorScalerType
import play.api.libs.json.{Format, Json}
import play.api.mvc.QueryStringBindable

object QueryStringBinders {

  class JsonQueryStringBinder[E:Format](implicit stringBinder: QueryStringBindable[String]) extends QueryStringBindable[E] {

    override def bind(
      key: String,
      params: Map[String, Seq[String]]
    ): Option[Either[String, E]] = {
      for {
        jsonString <- stringBinder.bind(key, params)
      } yield {
        jsonString match {
          case Right(jsonString) => {
            try {
              val filterJson = Json.parse(jsonString)
              Right(filterJson.as[E])
            } catch {
              case e: JsonParseException => Left("Unable to bind JSON from String to " + key)
            }
          }
          case _ => Left("Unable to bind JSON from String to " + key)
        }
      }
    }

    override def unbind(key: String, filterSpec: E): String =
      stringBinder.unbind(key, Json.stringify(Json.toJson(filterSpec)))
  }

  implicit val filterConditionQueryStringBinder = new JsonQueryStringBinder[Seq[FilterCondition]]
  implicit val filterQueryStringBinder = new JsonQueryStringBinder[Filter]
  implicit val fieldTypeIdsQueryStringBinder = new JsonQueryStringBinder[Seq[FieldTypeId.Value]]
  implicit val BSONObjectIDQueryStringBinder = BSONObjectIDQueryStringBindable
  implicit val filterOrIdBinder = new JsonQueryStringBinder[FilterOrId]
  implicit val filterOrIdSeqBinder = new JsonQueryStringBinder[Seq[FilterOrId]]
  implicit val tablePageSeqBinder = new JsonQueryStringBinder[Seq[PageOrder]]

  implicit val classificationRunSpecBinder = new JsonQueryStringBinder[ClassificationRunSpec]
  implicit val temporalClassificationRunSpecBinder = new JsonQueryStringBinder[TemporalClassificationRunSpec]
  implicit val regressionRunSpecBinder = new JsonQueryStringBinder[RegressionRunSpec]
  implicit val temporalRegressionRunSpecBinder = new JsonQueryStringBinder[TemporalRegressionRunSpec]

  implicit val vectorScalerTypeQueryStringBinder = new EnumStringBindable(VectorScalerType)
  implicit val classificationEvalMetricQueryStringBinder = new EnumStringBindable(ClassificationEvalMetric)
  implicit val regressionEvalMetricQueryStringBinder = new EnumStringBindable(RegressionEvalMetric)
  implicit val aggTypeQueryStringBinder = new EnumStringBindable(AggType)
  implicit val correlationTypeStringBinder = new EnumStringBindable(CorrelationType)
}