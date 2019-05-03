package org.ada.web.controllers.dataset

import javax.inject.Inject
import org.incal.spark_ml.models.setting.TemporalClassificationRunSpec

class TemporalClassificationRunDispatcher @Inject()(
  val dscf: DataSetControllerFactory,
  factory: TemporalClassificationRunControllerFactory
) extends MLRunDispatcher[TemporalClassificationRunController](ControllerName.temporalClassificationRun)
    with TemporalClassificationRunController {

  override def controllerFactory = factory(_)

  override def launch(
    runSpec: TemporalClassificationRunSpec,
    saveResults: Boolean,
    saveBinCurves: Boolean
  ) = dispatch(_.launch(runSpec, saveResults, saveBinCurves))
}