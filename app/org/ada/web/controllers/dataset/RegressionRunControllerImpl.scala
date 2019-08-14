package org.ada.web.controllers.dataset

import akka.stream.Materializer
import org.ada.server.models.ml.regression.Regressor.RegressorIdentity
import org.incal.spark_ml.models.regression.{RegressionEvalMetric, Regressor}
import org.incal.spark_ml.models.result._
import play.api.libs.json._
import views.html.{regressionrun => view}

import scala.reflect.runtime.universe.TypeTag

abstract class RegressionRunControllerImpl[E <: RegressionResult : Format : TypeTag](implicit materializer: Materializer) extends MLRunControllerImpl[E, Regressor] {

  override protected val mlMethodName = (x: Regressor) => x.name.getOrElse("N/A")

  override protected def showView = { implicit ctx =>
    (view.show(router)(_, _, _, _)).tupled
  }

  override protected def listView = { implicit ctx =>
    (view.list(router)(_, _, _, _, _, _, _, _, _, _, _)).tupled
  }
}