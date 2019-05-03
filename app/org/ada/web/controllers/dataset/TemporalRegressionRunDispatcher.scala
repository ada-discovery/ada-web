package org.ada.web.controllers.dataset

import javax.inject.Inject
import org.incal.spark_ml.models.setting.TemporalRegressionRunSpec

class TemporalRegressionRunDispatcher @Inject()(
  val dscf: DataSetControllerFactory,
  factory: TemporalRegressionRunControllerFactory
) extends MLRunDispatcher[TemporalRegressionRunController](ControllerName.temporalRegressionRun)
    with TemporalRegressionRunController {

  override def controllerFactory = factory(_)

  override def launch(
    runSpec: TemporalRegressionRunSpec,
    saveResults: Boolean
  ) = dispatch(_.launch(runSpec, saveResults))
}