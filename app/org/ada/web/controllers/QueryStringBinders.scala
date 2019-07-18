package org.ada.web.controllers

import org.ada.server.models.Filter._
import org.ada.web.controllers.FilterConditionExtraFormats.eitherFilterOrIdFormat
import org.ada.server.models.{AggType, CorrelationType, FieldTypeId, Filter}
import org.ada.server.models.ml._
import org.ada.server.models.DataSetFormattersAndIds.enumTypeFormat
import org.ada.server.models.ml.classification.ClassificationResult.{standardClassificationRunSpecFormat, temporalClassificationRunSpecFormat}
import org.ada.server.models.ml.regression.RegressionResult.{standardRegressionRunSpecFormat, temporalRegressionRunSpecFormat}
import org.incal.core.FilterCondition
import org.incal.play.PageOrder
import org.incal.play.formatters.{EnumStringBindable, JsonQueryStringBindable}
import org.incal.spark_ml.models.classification.ClassificationEvalMetric
import org.incal.spark_ml.models.regression.RegressionEvalMetric
import org.incal.spark_ml.models.setting._
import org.incal.spark_ml.models.VectorScalerType

object QueryStringBinders {

  implicit val filterConditionQueryStringBinder = new JsonQueryStringBindable[Seq[FilterCondition]]
  implicit val filterQueryStringBinder = new JsonQueryStringBindable[Filter]
  implicit val fieldTypeIdsQueryStringBinder = new JsonQueryStringBindable[Seq[FieldTypeId.Value]]
  implicit val BSONObjectIDQueryStringBinder = BSONObjectIDQueryStringBindable
  implicit val filterOrIdBinder = new JsonQueryStringBindable[FilterOrId]
  implicit val filterOrIdSeqBinder = new JsonQueryStringBindable[Seq[FilterOrId]]
  implicit val tablePageSeqBinder = new JsonQueryStringBindable[Seq[PageOrder]]

  implicit val classificationRunSpecBinder = new JsonQueryStringBindable[ClassificationRunSpec]
  implicit val temporalClassificationRunSpecBinder = new JsonQueryStringBindable[TemporalClassificationRunSpec]
  implicit val regressionRunSpecBinder = new JsonQueryStringBindable[RegressionRunSpec]
  implicit val temporalRegressionRunSpecBinder = new JsonQueryStringBindable[TemporalRegressionRunSpec]

  implicit val vectorScalerTypeQueryStringBinder = new EnumStringBindable(VectorScalerType)
  implicit val classificationEvalMetricQueryStringBinder = new EnumStringBindable(ClassificationEvalMetric)
  implicit val regressionEvalMetricQueryStringBinder = new EnumStringBindable(RegressionEvalMetric)
  implicit val aggTypeQueryStringBinder = new EnumStringBindable(AggType)
  implicit val correlationTypeStringBinder = new EnumStringBindable(CorrelationType)
}