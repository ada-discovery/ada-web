package org.ada.web.controllers.dataset

import org.incal.spark_ml.models.setting.ClassificationRunSpec
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID

trait StandardClassificationRunController extends MLRunController {

  def launch(
    runSpec: ClassificationRunSpec,
    saveResults: Boolean,
    saveBinCurves: Boolean
  ): Action[AnyContent]

  def selectFeaturesAsAnovaChiSquare(
    inputFieldNames: Seq[String],
    outputFieldName: String,
    filterId: Option[BSONObjectID],
    featuresToSelectNum: Int
  ): Action[AnyContent]
}

trait StandardClassificationRunControllerFactory  {
  def apply(dataSetId: String): StandardClassificationRunController
}