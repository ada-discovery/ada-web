package org.ada.web.controllers.dataset

import org.incal.spark_ml.models.setting.RegressionRunSpec
import play.api.mvc.{Action, AnyContent}

trait StandardRegressionRunController extends MLRunController {

  def launch(
    runSpec: RegressionRunSpec,
    saveResults: Boolean
  ): Action[AnyContent]
}

trait StandardRegressionRunControllerFactory {
  def apply(dataSetId: String): StandardRegressionRunController
}