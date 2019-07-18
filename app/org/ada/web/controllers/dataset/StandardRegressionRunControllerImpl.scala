package org.ada.web.controllers.dataset

import akka.stream.Materializer
import javax.inject.Inject
import com.google.inject.assistedinject.Assisted
import org.ada.server.models.{DistributionWidgetSpec, _}
import org.ada.server.models.DataSetFormattersAndIds._
import org.ada.server.dataaccess.dataset.FilterRepoExtra._
import org.ada.server.models.ml.regression.RegressionResult.standardRegressionResultFormat
import org.ada.web.models.Widget.WidgetWrites
import org.ada.server.dataaccess.RepoTypes.{ClassifierRepo, RegressorRepo}
import org.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.data.Forms._
import play.api.libs.json._
import play.api.mvc.{Action, Request}
import org.ada.server.services.DataSetService
import org.ada.web.services.{DataSpaceService, WidgetGenerationService}
import org.ada.server.services.ml._
import org.ada.server.json.OrdinalEnumFormat
import org.incal.core.dataaccess.Criterion._
import org.incal.core.dataaccess.Criterion
import org.incal.spark_ml.MLResultUtil
import org.incal.spark_ml.models.regression.RegressionEvalMetric
import org.incal.spark_ml.models.result.StandardRegressionResult
import org.incal.spark_ml.models.setting.RegressionRunSpec
import org.incal.spark_ml.models.VectorScalerType
import org.ada.server.services.StatsService
import views.html.{regressionrun => view}

import scala.concurrent.{Future, TimeoutException}

protected[controllers] class StandardRegressionRunControllerImpl @Inject()(
  @Assisted dataSetId: String,
  dsaf: DataSetAccessorFactory,
  val mlMethodRepo: RegressorRepo,
  val mlService: MachineLearningService,
  val dataSetService: DataSetService,
  val dataSpaceService: DataSpaceService,
  val wgs: WidgetGenerationService)(
  implicit materializer: Materializer
) extends RegressionRunControllerImpl[StandardRegressionResult]
  with StandardRegressionRunController {

  override protected def dsa = dsaf(dataSetId).get
  override protected val repo = dsa.standardRegressionResultRepo

  override protected val router = new StandardRegressionRunRouter(dataSetId)

  override protected val entityNameKey = "regressionRun"
  override protected val exportFileNamePrefix = "regression_results_"

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
    (view.create(_, _, _)).tupled
  }

  override def launch(
    runSpec: RegressionRunSpec,
    saveResults: Boolean
  ) = Action.async { implicit request => {
    println(runSpec)

    val mlModelFuture = mlMethodRepo.get(runSpec.mlModelId)
    val criteriaFuture = loadCriteria(runSpec.ioSpec.filterId)
    val replicationCriteriaFuture = loadCriteria(runSpec.ioSpec.replicationFilterId)

    val fieldNames = runSpec.ioSpec.allFieldNames
    val fieldsFuture = dsa.fieldRepo.find(Seq(FieldIdentity.name #-> fieldNames))

    def find(criteria: Seq[Criterion[Any]]) =
      dsa.dataSetRepo.find(criteria, projection = fieldNames)

    for {
      // load a ML model
      mlModel <- mlModelFuture

      // criteria
      criteria <- criteriaFuture

      // replication criteria
      replicationCriteria <- replicationCriteriaFuture

      // main data
      mainData <- find(criteria)

      // fields
      fields <- fieldsFuture

      // replication data
      replicationData <- if (replicationCriteria.nonEmpty) find(replicationCriteria) else Future(Nil)

      // run the selected classifier (ML model)
      resultsHolder <- mlModel.map { mlModel =>
        val fieldNameAndSpecs = fields.toSeq.map(field => (field.name, field.fieldTypeSpec))
        val results = mlService.regressStatic(mainData, fieldNameAndSpecs, runSpec.ioSpec.outputFieldName, mlModel, runSpec.learningSetting, replicationData)
        results.map(Some(_))
      }.getOrElse(
        Future(None)
      )
    } yield
      resultsHolder.map { resultsHolder =>
        // prepare the results stats
        val metricStatsMap = MLResultUtil.calcMetricStats(resultsHolder.performanceResults)

        if (saveResults) {
          val finalResult = MLResultUtil.createStandardRegressionResult(runSpec, metricStatsMap)
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
    org.ada.server.models.ml.regression.RegressionResult.createStandardRegressionResultFormat(
      OrdinalEnumFormat(VectorScalerType),
      OrdinalEnumFormat(RegressionEvalMetric)
    )
}