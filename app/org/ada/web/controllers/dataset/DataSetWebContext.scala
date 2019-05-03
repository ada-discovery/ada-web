package org.ada.web.controllers.dataset

import be.objectify.deadbolt.scala.AuthenticatedRequest
import controllers.WebJarAssets
import org.incal.play.controllers.WebContext
import play.api.Configuration
import play.api.i18n.Messages
import play.api.mvc.Flash

class DataSetWebContext(
  val dataSetId: String)(
  implicit val flash: Flash, val msg: Messages, val request: AuthenticatedRequest[_], val webJarAssets: WebJarAssets, val configuration: Configuration) {

  val dataSetRouter = new DataSetRouter(dataSetId)
  val dataSetJsRouter = new DataSetJsRouter(dataSetId)
  val dictionaryRouter = new DictionaryRouter(dataSetId)
  val dictionaryJsRouter = new DictionaryJsRouter(dataSetId)
  val categoryRouter = new CategoryRouter(dataSetId)
  val categoryJsRouter = new CategoryJsRouter(dataSetId)
  val filterRouter = new FilterRouter(dataSetId)
  val filterJsRouter = new FilterJsRouter(dataSetId)
  val dataViewRouter = new DataViewRouter(dataSetId)
  val dataViewJsRouter = new DataViewJsRouter(dataSetId)

  // ML routers

  val standardClassificationRunRouter = new StandardClassificationRunRouter(dataSetId)
  val standardClassificationRunJsRouter = new StandardClassificationRunJsRouter(dataSetId)
  val temporalClassificationRunRouter = new TemporalClassificationRunRouter(dataSetId)
  val temporalClassificationRunJsRouter = new TemporalClassificationRunJsRouter(dataSetId)
  val standardRegressionRunRouter = new StandardRegressionRunRouter(dataSetId)
  val standardRegressionRunJsRouter = new StandardRegressionRunJsRouter(dataSetId)
  val temporalRegressionRunRouter = new TemporalRegressionRunRouter(dataSetId)
  val temporalRegressionRunJsRouter = new TemporalRegressionRunJsRouter(dataSetId)
}

object DataSetWebContext {
  implicit def apply(
    dataSetId: String)(
    implicit context: WebContext
  ) =
    new DataSetWebContext(dataSetId)(context.flash, context.msg, context.request, context.webJarAssets, context.configuration)

  implicit def toFlash(
    implicit webContext: DataSetWebContext
  ): Flash = webContext.flash

  implicit def toMessages(
    implicit webContext: DataSetWebContext
  ): Messages = webContext.msg

  implicit def toRequest(
    implicit webContext: DataSetWebContext
  ): AuthenticatedRequest[_] = webContext.request

  implicit def toWebJarAssets(
    implicit webContext: DataSetWebContext
  ): WebJarAssets = webContext.webJarAssets

  implicit def configuration(
    implicit webContext: DataSetWebContext
  ): Configuration = webContext.configuration

  implicit def toWebContext(
    implicit webContext: DataSetWebContext
  ): WebContext = WebContext()

  implicit def dataSetRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.dataSetRouter

  def dataSetJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.dataSetJsRouter

  def dictionaryRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.dictionaryRouter

  def dictionaryJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.dictionaryJsRouter

  def categoryRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.categoryRouter

  def categoryJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.categoryJsRouter

  def filterRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.filterRouter

  def filterJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.filterJsRouter

  def dataViewRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.dataViewRouter

  def dataViewJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.dataViewJsRouter

  def standardClassificationRunRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.standardClassificationRunRouter

  def standardClassificationRunJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.standardClassificationRunJsRouter

  def temporalClassificationRunRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.temporalClassificationRunRouter

  def temporalClassificationRunJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.temporalClassificationRunJsRouter

  def standardRegressionRunRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.standardRegressionRunRouter

  def standardRegressionRunJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.standardRegressionRunJsRouter

  def temporalRegressionRunRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.temporalRegressionRunRouter

  def temporalRegressionRunJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.temporalRegressionRunJsRouter
}