package org.ada.web.controllers.dataset

import javax.inject.Inject
import org.incal.play.controllers.SecureControllerDispatcher
import org.incal.core.FilterCondition
import org.incal.play.security.SecurityRole
import reactivemongo.bson.BSONObjectID
import org.ada.web.models.security.DataSetPermission
import org.incal.spark_ml.models.setting.RegressionRunSpec

class StandardRegressionRunDispatcher @Inject()(
  val dscf: DataSetControllerFactory,
  factory: StandardRegressionRunControllerFactory
) extends MLRunDispatcher[StandardRegressionRunController](ControllerName.regressionRun)
    with StandardRegressionRunController {

  override def controllerFactory = factory(_)

  override def launch(
    runSpec: RegressionRunSpec,
    saveResults: Boolean
  ) = dispatch(_.launch(runSpec, saveResults))
}