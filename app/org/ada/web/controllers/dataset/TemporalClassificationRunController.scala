package org.ada.web.controllers.dataset

import org.incal.spark_ml.models.setting.TemporalClassificationRunSpec
import play.api.mvc.{Action, AnyContent}

trait TemporalClassificationRunController extends MLRunController {

  def launch(
    runSpec: TemporalClassificationRunSpec,
    saveResults: Boolean,
    saveBinCurves: Boolean
  ): Action[AnyContent]
}

trait TemporalClassificationRunControllerFactory {
  def apply(dataSetId: String): TemporalClassificationRunController
}