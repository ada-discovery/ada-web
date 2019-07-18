package org.ada.web.controllers.dataset

import akka.stream.Materializer
import com.google.inject.assistedinject.Assisted
import javax.inject.Inject
import org.ada.server.AdaException
import org.ada.server.models.DataSetFormattersAndIds._
import org.ada.server.json.OrdinalEnumFormat
import org.ada.server.models.ml.regression.RegressionResult.temporalRegressionResultFormat
import org.ada.server.models.{DistributionWidgetSpec, _}
import org.incal.core.dataaccess.Criterion
import org.incal.core.dataaccess.Criterion._
import org.incal.spark_ml.MLResultUtil
import org.incal.spark_ml.models.VectorScalerType
import org.incal.spark_ml.models.regression.RegressionEvalMetric
import org.incal.spark_ml.models.result.TemporalRegressionResult
import org.incal.spark_ml.models.setting.{RegressionRunSpec, TemporalRegressionRunSpec}
import org.ada.server.field.FieldUtil.FieldOps
import org.ada.server.dataaccess.RepoTypes.RegressorRepo
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.mvc.Action
import org.ada.server.services.ml._
import org.ada.server.services.DataSetService
import org.ada.web.services.{DataSpaceService, WidgetGenerationService}
import views.html.{regressionrun => view}

import scala.concurrent.Future

protected[controllers] class TemporalRegressionRunControllerImpl @Inject()(
  @Assisted dataSetId: String,
  dsaf: DataSetAccessorFactory,
  val mlMethodRepo: RegressorRepo,
  val mlService: MachineLearningService,
  val dataSetService: DataSetService,
  val dataSpaceService: DataSpaceService,
  val wgs: WidgetGenerationService)(
  implicit materializer: Materializer
) extends RegressionRunControllerImpl[TemporalRegressionResult]
  with TemporalRegressionRunController {

  override protected def dsa = dsaf(dataSetId).get
  override protected val repo = dsa.temporalRegressionResultRepo

  override protected val router = new TemporalRegressionRunRouter(dataSetId)

  override protected val entityNameKey = "temporalRegressionRun"
  override protected val exportFileNamePrefix = "regression_results_"
  override protected val excludedFieldNames = Seq("reservoirSetting")

  private val distributionDisplayOptions = MultiChartDisplayOptions(chartType = Some(ChartType.Column), gridWidth = Some(3))

  override protected val widgetSpecs = Seq(
    DistributionWidgetSpec("testStats-mse-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-rmse-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-r2-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-mae-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("timeCreated", None, displayOptions = MultiChartDisplayOptions(chartType = Some(ChartType.Column))),
    ScatterWidgetSpec("trainingStats-mse-mean", "testStats-mse-mean", Some("runSpec-mlModelId")),
    ScatterWidgetSpec("testStats-r2-mean", "testStats-mse-mean", Some("runSpec-mlModelId"))
  )

  override protected val listViewColumns = Some(Seq(
    "runSpec-mlModelId",
    "runSpec-ioSpec-filterId",
    "runSpec-ioSpec-outputFieldName",
    "testStats-mae-mean",
    "testStats-mse-mean",
    "testStats-rmse-mean",
    "testStats-r2-mean",
    "timeCreated"
  ))

  override protected def createView = { implicit ctx =>
    (view.createTemporal(_, _, _)).tupled
  }

  override def launch(
    runSpec: TemporalRegressionRunSpec,
    saveResults: Boolean
  ) = Action.async { implicit request => {
    val ioSpec = runSpec.ioSpec

    val mlModelFuture = mlMethodRepo.get(runSpec.mlModelId)
    val criteriaFuture = loadCriteria(runSpec.ioSpec.filterId)
    val replicationCriteriaFuture = loadCriteria(runSpec.ioSpec.replicationFilterId)

    val fieldNames = runSpec.ioSpec.allFieldNames
    val fieldsFuture = dsa.fieldRepo.find(Seq(FieldIdentity.name #-> fieldNames))

    def find(criteria: Seq[Criterion[Any]], orderedValues: Seq[Any]) = {
      val orderedValuesOnly = if (orderedValues.nonEmpty)
        Seq(ioSpec.orderFieldName #-> orderedValues)
      else
        Nil

      dsa.dataSetRepo.find(criteria ++ orderedValuesOnly, projection = fieldNames)
    }

    for {
      // load a ML model
      mlModel <- mlModelFuture

      // criteria
      criteria <- criteriaFuture

      // replication criteria
      replicationCriteria <- replicationCriteriaFuture

      // fields
      fields <- fieldsFuture

      // order field
      orderField = fields.find(_.name == ioSpec.orderFieldName).getOrElse(throw new AdaException(s"Order field ${ioSpec.outputFieldName} not found."))
      orderFieldType = ftf(orderField.fieldTypeSpec).asValueOf[Any]
      orderedValues = if (ioSpec.orderedStringValues.isEmpty && (orderField.isEnum || orderField.isString)) {
        throw new AdaException(s"String (display) values in fixed order required for the ${orderField.fieldType} order field ${ioSpec.orderFieldName}.")
      } else
        ioSpec.orderedStringValues.map(x => orderFieldType.displayStringToValue(x).get)

      // main data
      mainData <- find(criteria, orderedValues)

      // replication data
      replicationData <- if (replicationCriteria.nonEmpty) find(replicationCriteria, orderedValues) else Future(Nil)

      // run the selected classifier (ML model)
      resultsHolder <- mlModel.map { mlModel =>

        val fieldNameAndSpecs = fields.toSeq.map(field => (field.name, field.fieldTypeSpec))

        val results = mlService.regressRowTemporalSeries(
          mainData, fieldNameAndSpecs, ioSpec.inputFieldNames, ioSpec.outputFieldName, ioSpec.orderFieldName, orderedValues, Some(ioSpec.groupIdFieldName),
          mlModel, runSpec.learningSetting, replicationData
        )
        results.map(Some(_))
      }.getOrElse(
        Future(None)
      )
    } yield
      resultsHolder.map { resultsHolder =>
        // prepare the results stats
        val metricStatsMap = MLResultUtil.calcMetricStats(resultsHolder.performanceResults)

        if (saveResults) {
          val finalResult = MLResultUtil.createTemporalRegressionResult(runSpec, metricStatsMap)
          repo.save(finalResult)
        }

        val resultsJson = resultsToJson(RegressionEvalMetric)(metricStatsMap)

        logger.info("Regression finished with the following results:\n" + Json.prettyPrint(resultsJson))

        Ok(resultsJson)
      }.getOrElse(
        BadRequest(s"ML regression model with id ${runSpec.mlModelId.stringify} not found.")
      )
    }.recover(handleExceptionsWithErrorCodes("a launch"))
  }

  override protected def exportFormat=
    org.ada.server.models.ml.regression.RegressionResult.createTemporalRegressionResultFormat(
      OrdinalEnumFormat(VectorScalerType),
      OrdinalEnumFormat(RegressionEvalMetric)
    )
}