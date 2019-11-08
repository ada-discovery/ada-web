package org.ada.web.controllers.dataset

import com.google.inject.assistedinject.Assisted
import javax.inject.Inject
import org.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.ada.server.models._
import org.incal.spark_ml.models.result.{BinaryClassificationCurves, ClassificationResult, MetricStatsValues, StandardClassificationResult}
import org.ada.server.models.ml.classification.ClassificationResult.standardClassificationResultFormat
import org.incal.core.dataaccess.{AsyncCrudRepo, Criterion}
import org.incal.spark_ml.MLResultUtil
import org.incal.spark_ml.models.setting.ClassificationRunSpec
import Criterion.Infix
import akka.stream.Materializer
import org.ada.server.json.OrdinalEnumFormat
import org.incal.spark_ml.models.VectorScalerType
import org.incal.spark_ml.models.classification.ClassificationEvalMetric
import org.ada.server.dataaccess.RepoTypes.ClassifierRepo
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID
import org.ada.server.services.DataSetService
import org.ada.web.services.{DataSpaceService, WidgetGenerationService}
import org.ada.server.services.ml.MachineLearningService
import org.ada.server.services.StatsService
import views.html.{classificationrun => view}
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

class StandardClassificationRunControllerImpl @Inject()(
  @Assisted dataSetId: String,
  dsaf: DataSetAccessorFactory,
  val mlMethodRepo: ClassifierRepo,
  val mlService: MachineLearningService,
  val statsService: StatsService,
  val dataSetService: DataSetService,
  val dataSpaceService: DataSpaceService,
  val wgs: WidgetGenerationService)(
  implicit materializer: Materializer
) extends ClassificationRunControllerImpl[StandardClassificationResult]
    with StandardClassificationRunController {

  override protected def dsa = dsaf(dataSetId).get
  override protected val repo = dsa.standardClassificationRepo

  override protected val router = new StandardClassificationRunRouter(dataSetId)

  override protected val entityNameKey = "classificationRun"
  override protected val exportFileNamePrefix = "classification_results_"

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
    (view.create(_, _, _)).tupled
  }

  override def launch(
    runSpec: ClassificationRunSpec,
    saveResults: Boolean,
    saveBinCurves: Boolean
  ) = Action.async { implicit request => {
    val mlModelFuture = mlMethodRepo.get(runSpec.mlModelId)
    val criteriaFuture = loadCriteria(runSpec.ioSpec.filterId)
    val replicationCriteriaFuture = loadCriteria(runSpec.ioSpec.replicationFilterId)

    val fieldNames = runSpec.ioSpec.allFieldNames
    val fieldsFuture = dsa.fieldRepo.find(Seq(FieldIdentity.name #-> fieldNames))

    for {
      // load a ML model
      mlModel <- mlModelFuture

      // criteria
      criteria <- criteriaFuture

      // replication criteria
      replicationCriteria <- replicationCriteriaFuture

      // fields
      fields <- fieldsFuture

      // replication data
      replicationData <- if (replicationCriteria.nonEmpty) dsa.dataSetRepo.find(replicationCriteria, projection = fieldNames) else Future(Nil)

      // selected fields
      selectedFields <-
        runSpec.learningSetting.featuresSelectionNum.map { featuresSelectionNum =>
          val inputFields = fields.filter(!_.name.equals(runSpec.ioSpec.outputFieldName))
          val outputField = fields.find(_.name.equals(runSpec.ioSpec.outputFieldName)).get
          statsService.selectFeaturesAsAnovaChiSquare(dsa.dataSetRepo, criteria, inputFields.toSeq, outputField, featuresSelectionNum).map {
            selectedInputFields => selectedInputFields ++ Seq(outputField)
          }
        }.getOrElse(
          Future(fields)
        )

      // main data
      mainData <- dsa.dataSetRepo.find(replicationCriteria, projection = selectedFields.map(_.name))

      // run the selected classifier (ML model)
      resultsHolder <- mlModel.map { mlModel =>
        val fieldNameAndSpecs = selectedFields.toSeq.map(field => (field.name, field.fieldTypeSpec))
        val results = mlService.classifyStatic(mainData, fieldNameAndSpecs, runSpec.ioSpec.outputFieldName, mlModel, runSpec.learningSetting, replicationData)
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
          val finalResult = MLResultUtil.createStandardClassificationResult(runSpec, metricStatsMap, binCurves)
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

  override def selectFeaturesAsAnovaChiSquare(
    inputFieldNames: Seq[String],
    outputFieldName: String,
    filterId: Option[BSONObjectID],
    featuresToSelectNum: Int
  ) = Action.async { implicit request =>
    val explFieldNamesToLoads = (inputFieldNames ++ Seq(outputFieldName)).toSet.toSeq

    val criteriaFuture = loadCriteria(filterId)

    for {
      criteria <- criteriaFuture
      fields <- dsa.fieldRepo.find(Seq(FieldIdentity.name #-> explFieldNamesToLoads))
      selectedFields <- {
        val inputFields = fields.filter(!_.name.equals(outputFieldName)).toSeq
        val outputField = fields.find(_.name.equals(outputFieldName)).get
        statsService.selectFeaturesAsAnovaChiSquare(dsa.dataSetRepo, criteria, inputFields, outputField, featuresToSelectNum)
      }
    } yield {
      val json = JsArray(selectedFields.map(field => JsString(field.name)))
      Ok(json)
    }
  }

  override protected def exportFormat=
    org.ada.server.models.ml.classification.ClassificationResult.createStandardClassificationResultFormat(
      OrdinalEnumFormat(VectorScalerType),
      OrdinalEnumFormat(ClassificationEvalMetric)
    )

  override protected def alterExportJson(resultJson: JsObject): JsObject = {
    // handle sampling ratios, which are stored as an unstructured array
    val newSamplingRatioJson = (resultJson \ "runSpec-learningSetting-samplingRatios").asOpt[JsArray].map { jsonArray =>
      val samplingRatioJsons = jsonArray.value.map { case json: JsArray =>
        val outputValue = json.value(0)
        val samplingRatio = json.value(1)
        Json.obj("outputValue" -> outputValue, "samplingRatio" -> samplingRatio)
      }
      JsArray(samplingRatioJsons)
    }.getOrElse(JsNull)

    resultJson.+("runSpec-learningSetting-samplingRatios", newSamplingRatioJson)
  }
}