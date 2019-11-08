package org.ada.web.controllers.dataset

import akka.stream.Materializer
import com.google.inject.assistedinject.Assisted
import javax.inject.Inject
import org.ada.server.AdaException
import org.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.ada.server.models._
import org.ada.server.json.OrdinalEnumFormat
import org.ada.server.models.ml.classification.ClassificationResult.temporalClassificationResultFormat
import org.incal.core.dataaccess.Criterion
import org.incal.core.dataaccess.Criterion.Infix
import org.incal.spark_ml.MLResultUtil
import org.incal.spark_ml.models.VectorScalerType
import org.incal.spark_ml.models.classification.ClassificationEvalMetric
import org.incal.spark_ml.models.result.TemporalClassificationResult
import org.incal.spark_ml.models.setting.{ClassificationRunSpec, TemporalClassificationRunSpec}
import org.ada.server.dataaccess.RepoTypes.ClassifierRepo
import org.ada.server.field.FieldUtil.FieldOps
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.mvc.Action
import org.ada.server.services.ml.MachineLearningService
import org.ada.server.services.StatsService
import org.ada.server.services.DataSetService
import org.ada.web.services.{DataSpaceService, WidgetGenerationService}
import views.html.{classificationrun => view}

import scala.concurrent.Future

class TemporalClassificationRunControllerImpl @Inject()(
  @Assisted dataSetId: String,
  dsaf: DataSetAccessorFactory,
  val mlMethodRepo: ClassifierRepo,
  val mlService: MachineLearningService,
  val statsService: StatsService,
  val dataSetService: DataSetService,
  val dataSpaceService: DataSpaceService,
  val wgs: WidgetGenerationService)(
  implicit materializer: Materializer
) extends ClassificationRunControllerImpl[TemporalClassificationResult]
    with TemporalClassificationRunController {

  override protected def dsa = dsaf(dataSetId).get
  override protected val repo = dsa.temporalClassificationRepo

  override protected val router = new TemporalClassificationRunRouter(dataSetId)

  override protected val entityNameKey = "temporalClassificationRun"
  override protected val exportFileNamePrefix = "temporal_classification_results_"
  override protected val excludedFieldNames = Seq("reservoirSetting")

  private val distributionDisplayOptions = MultiChartDisplayOptions(chartType = Some(ChartType.Column), gridWidth = Some(3))

  override protected val widgetSpecs = Seq(
    DistributionWidgetSpec("testStats-accuracy-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-weightedPrecision-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-weightedRecall-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-f1-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-areaUnderROC-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-areaUnderPR-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("timeCreated", None, displayOptions = MultiChartDisplayOptions(chartType = Some(ChartType.Column))),
    ScatterWidgetSpec("trainingStats-accuracy-mean", "testStats-accuracy-mean", Some("runSpec-mlModelId")),
    ScatterWidgetSpec("testStats-areaUnderROC-mean", "testStats-accuracy-mean", Some("runSpec-mlModelId"))
  )

  override protected val listViewColumns = Some(Seq(
    "runSpec-mlModelId",
    "runSpec-ioSpec-filterId",
    "runSpec-ioSpec-outputFieldName",
    "testStats-accuracy-mean",
    "testStats-weightedPrecision-mean",
    "testStats-weightedRecall-mean",
    "testStats-f1-mean",
    "testStats-areaUnderROC-mean",
    "testStats-areaUnderPR-mean",
    "timeCreated"
  ))

  override protected def createView = { implicit ctx =>
    (view.createTemporal(_, _, _)).tupled
  }

  override def launch(
    runSpec: TemporalClassificationRunSpec,
    saveResults: Boolean,
    saveBinCurves: Boolean
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

      // ordered values criteria
      orderedValuesOnlyCriteria =
        if (orderedValues.nonEmpty)
          Seq(ioSpec.orderFieldName #-> orderedValues)
        else
          Nil

      // main data
      mainData <- find(criteria, orderedValues)

      // replication data
      replicationData <- if (replicationCriteria.nonEmpty) find(replicationCriteria, orderedValues) else Future(Nil)

      // select features
      selectedInputFieldNames <- runSpec.learningSetting.core.featuresSelectionNum.map { featuresSelectionNum =>
        val inputFields = fields.filter(field => ioSpec.inputFieldNames.contains(field.name))
        val outputField = fields.find(_.name == ioSpec.outputFieldName).getOrElse(throw new AdaException(s"Output field ${ioSpec.outputFieldName} not found."))
        statsService.selectFeaturesAsAnovaChiSquare(dsa.dataSetRepo, criteria ++ orderedValuesOnlyCriteria, inputFields.toSeq, outputField, featuresSelectionNum).map {
          _.map(_.name)
        }
      }.getOrElse(
        Future(ioSpec.inputFieldNames)
      )

      // run the selected classifier (ML model)
      resultsHolder <- mlModel.map { mlModel =>
        val actualFieldNames = ioSpec.copy(inputFieldNames = selectedInputFieldNames).allFieldNames

        val fieldNameAndSpecs = fields.toSeq.filter(field => actualFieldNames.contains(field.name)).map(field => (field.name, field.fieldTypeSpec))

        val results = mlService.classifyRowTemporalSeries(
          mainData, fieldNameAndSpecs, selectedInputFieldNames, ioSpec.outputFieldName, ioSpec.orderFieldName, orderedValues, Some(ioSpec.groupIdFieldName),
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
          val binCurves = if (saveBinCurves) resultsHolder.binCurves else Nil
          val finalResult = MLResultUtil.createTemporalClassificationResult(runSpec, metricStatsMap, binCurves)
          repo.save(finalResult)
        }

        val resultsJson = resultsToJson(ClassificationEvalMetric)(metricStatsMap)

        val replicationCurves = binCurvesToWidgets(resultsHolder.binCurves.flatMap(_._3), 350)
        val height = if (replicationCurves.nonEmpty) 350 else 500
        val trainingCurves = binCurvesToWidgets(resultsHolder.binCurves.flatMap(_._1), height)
        val testCurves = binCurvesToWidgets(resultsHolder.binCurves.flatMap(_._2), height)

        logger.info("Classification finished with the following results:\n" + Json.prettyPrint(resultsJson))

        val json = Json.obj(
          "results" -> resultsJson,
          "trainingCurves" -> Json.toJson(trainingCurves.toSeq),
          "testCurves" -> Json.toJson(testCurves.toSeq),
          "replicationCurves" -> Json.toJson(replicationCurves.toSeq)
        )
        Ok(json)
      }.getOrElse(
        BadRequest(s"ML classification model with id ${runSpec.mlModelId.stringify} not found.")
      )
    }.recover(handleExceptionsWithErrorCodes("a launch"))
  }

  override protected def exportFormat =
    org.ada.server.models.ml.classification.ClassificationResult.createTemporalClassificationResultFormat(
      OrdinalEnumFormat(VectorScalerType),
      OrdinalEnumFormat(ClassificationEvalMetric)
    )

  override protected def alterExportJson(resultJson: JsObject): JsObject = {
    // handle sampling ratios, which are stored as an unstructured array
    val newSamplingRatioJson = (resultJson \ "runSpec-learningSetting-core-samplingRatios").asOpt[JsArray].map { jsonArray =>
      val samplingRatioJsons = jsonArray.value.map { case json: JsArray =>
        val outputValue = json.value(0)
        val samplingRatio = json.value(1)
        Json.obj("outputValue" -> outputValue, "samplingRatio" -> samplingRatio)
      }
      JsArray(samplingRatioJsons)
    }.getOrElse(JsNull)

    resultJson.+("runSpec-learningSetting-core-samplingRatios", newSamplingRatioJson)
  }
}