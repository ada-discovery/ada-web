package org.ada.web.controllers.dataset

import org.incal.spark_ml.models.setting.{RegressionRunSpec, TemporalRegressionRunSpec}
import play.api.mvc.{Action, AnyContent}

trait TemporalRegressionRunController extends MLRunController {

  def launch(
    runSpec: TemporalRegressionRunSpec,
    saveResults: Boolean
  ): Action[AnyContent]
}

trait TemporalRegressionRunControllerFactory {
  def apply(dataSetId: String): TemporalRegressionRunController
}