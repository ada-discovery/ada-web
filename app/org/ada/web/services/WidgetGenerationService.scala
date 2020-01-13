package org.ada.web.services

import javax.inject.{Inject, Singleton}
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}
import com.google.inject.ImplementedBy
import org.ada.web.models.{HtmlWidget, Widget}
import org.ada.server.dataaccess.RepoTypes.JsonReadonlyRepo
import org.ada.server.models._
import org.ada.server.AdaException
import org.incal.core.dataaccess.Criterion
import org.incal.core.util.GroupMapList
import play.api.{Configuration, Logger}
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID
import org.ada.web.services.widgetgen._
import org.ada.server.calc.CalculatorTypePack
import org.ada.server.services.StatsService
import org.ada.server.calc.CalculatorHelper._
import org.incal.core.akka.AkkaStreamUtil
import org.ada.server.field.FieldUtil._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.sequence

@ImplementedBy(classOf[WidgetGenerationServiceImpl])
trait WidgetGenerationService {

  def apply(
    widgetSpecs: Traversable[WidgetSpec],
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]] = Nil,
    widgetFilterSubCriteriaMap: Map[BSONObjectID, Seq[Criterion[Any]]] = Map(),
    fields: Traversable[Field],
    genMethod: WidgetGenerationMethod.Value
  ): Future[Traversable[Option[Widget]]]

  def applyWithFields(
    widgetSpecs: Traversable[WidgetSpec],
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]] = Nil,
    widgetFilterSubCriteriaMap: Map[BSONObjectID, Seq[Criterion[Any]]] = Map(),
    fields: Traversable[Field],
    genMethod: WidgetGenerationMethod.Value
  ): Future[Traversable[Option[(Widget, Seq[String])]]]

  def genFromFullData(
    widgetSpec: WidgetSpec,
    items: Traversable[JsObject],
    criteria: Seq[Criterion[Any]],
    fields: Traversable[Field]
  ): Seq[Option[Widget]]

  def genStreamed(
    widgetSpec: WidgetSpec,
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    fields: Traversable[Field],
    widgetFilterSubCriteriaMap: Map[BSONObjectID, Seq[Criterion[Any]]] = Map()
  ): Future[Seq[Option[Widget]]]

  def genFromRepo(
    widgetSpec: WidgetSpec,
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    fields: Traversable[Field]
  ): Future[Seq[Option[Widget]]]
}

@Singleton
class WidgetGenerationServiceImpl @Inject() (
    statsService: StatsService,
    configuration: Configuration
  ) extends WidgetGenerationService {

  private val streamedCorrelationCalcParallelism = configuration.getInt("streamedcalc.correlation.parallelism").getOrElse(4)

  private val logger = Logger

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  import statsService._
  import WidgetGenerationMethod._

  override def apply(
    widgetSpecs: Traversable[WidgetSpec],
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    widgetFilterSubCriteriaMap: Map[BSONObjectID, Seq[Criterion[Any]]],
    fields: Traversable[Field],
    genMethod: WidgetGenerationMethod.Value
  ): Future[Traversable[Option[Widget]]] =
    applyWithFields(
      widgetSpecs, repo, criteria, widgetFilterSubCriteriaMap, fields, genMethod
    ).map(_.map(_.map(_._1)))

  override def applyWithFields(
    widgetSpecs: Traversable[WidgetSpec],
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    widgetFilterSubCriteriaMap: Map[BSONObjectID, Seq[Criterion[Any]]],
    fields: Traversable[Field],
    genMethod: WidgetGenerationMethod.Value
  ): Future[Traversable[Option[(Widget, Seq[String])]]] = {
    // TODO: if auto decide which method to use intelligently
    // check the number of rows x fields - if < threshold (e.g. 200k) do RepoAndFullData otherwise RepoAndStreamedFull
    // can take the repo type (Mongo vs Elastic) into account
    val initGenMethod =
      if (genMethod == WidgetGenerationMethod.Auto)
        WidgetGenerationMethod.RepoAndFullData
      else
        genMethod

    applyAux(widgetSpecs, repo, criteria, widgetFilterSubCriteriaMap, fields, initGenMethod)
  }

  private def applyAux(
    widgetSpecs: Traversable[WidgetSpec],
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    widgetFilterSubCriteriaMap: Map[BSONObjectID, Seq[Criterion[Any]]],
    fields: Traversable[Field],
    genMethod: WidgetGenerationMethod.Value
  ): Future[Traversable[Option[(Widget, Seq[String])]]] = {
    val nameFieldMap = fields.map(field => (field.name, field)).toMap

    def isScalar(fieldName: String) =
      nameFieldMap.get(fieldName).map(!_.isArray).getOrElse(true)

    val splitWidgetSpecs: Traversable[Either[WidgetSpec, WidgetSpec]] =
      if (genMethod.isRepoBased) {
        widgetSpecs.collect {
          case p: DistributionWidgetSpec => if (isScalar(p.fieldName)) Left(p) else Right(p)
          case p: CategoricalCheckboxWidgetSpec => if (isScalar(p.fieldName)) Left(p) else Right(p)
          case p: BoxWidgetSpec => if (isScalar(p.fieldName)) Left(p) else Right(p)
          case p: CumulativeCountWidgetSpec => if (p.numericBinCount.isDefined && isScalar(p.fieldName)) Left(p) else Right(p)
          case p: CustomHtmlWidgetSpec => Left(p)
          case p: GridDistributionCountWidgetSpec => Left(p)
          case p: WidgetSpec => Right(p)
        }
      } else
        widgetSpecs.map(Right(_))

    val repoWidgetSpecs = splitWidgetSpecs.flatMap(_.left.toOption)
    val nonRepoWidgetSpecs = splitWidgetSpecs.flatMap(_.right.toOption)

    // future to generate widgets from repo
    val repoWidgetsFuture = genFromRepo(
      repoWidgetSpecs, repo, criteria, widgetFilterSubCriteriaMap, nameFieldMap
    )

    // future to generate widgets from full data or a stream (individual per widget or full with a flow broadcast)
    val nonRepoWidgetsFuture =
      if (nonRepoWidgetSpecs.nonEmpty)
        genMethod match {
          case RepoAndFullData | FullData =>
            genFromFullData(nonRepoWidgetSpecs, repo, criteria, widgetFilterSubCriteriaMap, nameFieldMap)

          case RepoAndStreamedIndividually | StreamedIndividually =>
            genStreamedIndividually(nonRepoWidgetSpecs, repo, criteria, widgetFilterSubCriteriaMap, nameFieldMap)

          case RepoAndStreamedAll | StreamedAll =>
            genStreamedAll(nonRepoWidgetSpecs, repo, criteria, widgetFilterSubCriteriaMap, nameFieldMap)
        }
      else
        Future(Nil)

    for {
      specWidgets1 <- repoWidgetsFuture
      specWidgets2 <- nonRepoWidgetsFuture
    } yield {
      val specWidgetMap = (specWidgets1 ++ specWidgets2).toMap

      // add field names
      val specWidgetFieldNamesMap = specWidgetMap.map { case (spec, widgets) =>
        spec -> widgets.map(_.map(widget => (widget, spec.fieldNames.toSeq)))
      }

      // return widgets (with field names) in the specified order
      widgetSpecs.flatMap(specWidgetFieldNamesMap.get).flatten
    }
  }

  private def genFromRepo(
    widgetSpecs: Traversable[WidgetSpec],
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    widgetFilterSubCriteriaMap: Map[BSONObjectID, Seq[Criterion[Any]]],
    nameFieldMap: Map[String, Field]
  ): Future[Traversable[(WidgetSpec, Seq[Option[Widget]])]] = sequence(
      widgetSpecs.par.map { widgetSpec =>
        val newCriteria = criteria ++ getSubCriteria(widgetFilterSubCriteriaMap)(widgetSpec.subFilterId)

        genFromRepoAux(
          widgetSpec, repo, newCriteria, nameFieldMap
        ).map(widget => (widgetSpec, Seq(widget)))

      }.toList
    )

  private def genStreamedIndividually(
    widgetSpecs: Traversable[WidgetSpec],
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    widgetFilterSubCriteriaMap: Map[BSONObjectID, Seq[Criterion[Any]]],
    nameFieldMap: Map[String, Field]
  ): Future[Traversable[(WidgetSpec, Seq[Option[Widget]])]] =
    for {
      // calc min maxes for requested fields and filter widgets without min/max
      (fieldNameMinMaxesDefined, filteredWidgetSpecs) <- createMinMaxMapAndFilterSpec(widgetSpecs, repo, criteria, widgetFilterSubCriteriaMap, nameFieldMap)

      // generate widgets
      widgets <- genStreamedIndividuallyAux(
        filteredWidgetSpecs,  repo, criteria, widgetFilterSubCriteriaMap, nameFieldMap, fieldNameMinMaxesDefined
      )
    } yield
      widgets

  private def genStreamedAll(
    widgetSpecs: Traversable[WidgetSpec],
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    widgetFilterSubCriteriaMap: Map[BSONObjectID, Seq[Criterion[Any]]],
    nameFieldMap: Map[String, Field]
  ): Future[Traversable[(WidgetSpec, Seq[Option[Widget]])]] =
    for {
      // calc min maxes for requested fields and filter widgets without min/max
      (fieldNameMinMaxesDefined, filteredWidgetSpecs) <- createMinMaxMapAndFilterSpec(widgetSpecs, repo, criteria, widgetFilterSubCriteriaMap, nameFieldMap)

      // group by sub filter id and generate widgets in batches with zipped flows (and post flows)
      specWidgets <- sequence(
        filteredWidgetSpecs.groupBy(_.subFilterId).map { case (subFilterId, groupedWidgetSpecs) =>
          val finalCriteria = criteria ++ getSubCriteria(widgetFilterSubCriteriaMap)(subFilterId)
          genStreamedAllAux(groupedWidgetSpecs, repo, finalCriteria, nameFieldMap, fieldNameMinMaxesDefined)
        }
      ).map(_.flatten)
    } yield
      specWidgets

  private def createMinMaxMapAndFilterSpec(
    widgetSpecs: Traversable[WidgetSpec],
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    widgetFilterSubCriteriaMap: Map[BSONObjectID, Seq[Criterion[Any]]],
    nameFieldMap: Map[String, Field]
  ): Future[(
      Map[(String, Option[BSONObjectID]), (Double, Double)],
      Traversable[WidgetSpec]
    )] = {

    // collect all the fields required for min/max calculation for all the widgets
    val widgetSpecMinMaxFields =
      widgetSpecs.map { widgetSpec =>
        val fields = flowMinMaxFields(widgetSpec, nameFieldMap)
        (widgetSpec, fields)
      }

    val minMaxSubFilterIdFields = widgetSpecMinMaxFields.flatMap { case (widgetSpec, fields) =>
      fields.map(field => (widgetSpec.subFilterId, field))
    }.toSet

    for {
      // calc min maxes for requested fields
      fieldNameSubFilterIdMinMaxMap <- sequence(
        minMaxSubFilterIdFields.map { case (subFilterId, field) =>
          val finalCriteria = criteria ++ getSubCriteria(widgetFilterSubCriteriaMap)(subFilterId)
          getNumericMinMax(repo, finalCriteria, field).map { case (min, max) => (field.name, subFilterId) -> (min, max) }
        }
      ).map(_.toMap)
    } yield {

      // check if all the min/max values requested by widgets are defined... if not filter out the unfulfilled widgets and log a warning message
      val filteredWidgetSpecs = widgetSpecMinMaxFields.map { case (widgetSpec, minMaxFields) =>
        val minMaxes = minMaxFields.map { field =>
          val (min, max) = fieldNameSubFilterIdMinMaxMap.get((field.name, widgetSpec.subFilterId)).get
          (field, min, max)
        }

        minMaxes.find{ case (_, min, max) => min.isEmpty || max.isEmpty }.map { fieldWithoutMinMax =>
          logger.warn(s"Cannot generate a widget of type ${widgetSpec.getClass.getName} because its field ${fieldWithoutMinMax._1.name} has no min/max values. Probably it doesn't contain any values or all are null.")
          None
        }.getOrElse{
          Some(widgetSpec)
        }
      }.flatten

      val map = fieldNameSubFilterIdMinMaxMap.collect{
        case ((fieldName, subFilterId), (Some(min), Some(max))) => ((fieldName, subFilterId), (min, max))
      }
      (map, filteredWidgetSpecs)
    }
  }

  private def genStreamedAllAux(
    widgetSpecs: Traversable[WidgetSpec],
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    nameFieldMap: Map[String, Field],
    fieldNameSubFilterIdMinMaxes: Map[(String, Option[BSONObjectID]), (Double, Double)]
  ): Future[Traversable[(WidgetSpec, Seq[Option[Widget]])]] = {

    // create (loaded) generators
    val specGenerators = widgetSpecs.map { widgetSpec =>
      val generators = createGenerator(widgetSpec, criteria, nameFieldMap, fieldNameSubFilterIdMinMaxes)
      (widgetSpec, generators)
    }.toSeq

    val definedGenerators = specGenerators.flatMap(_._2)

    // referenced field names
    val flowFieldNames = specGenerators.collect { case (spec, _) => spec.fieldNames }.flatten.toSet

    // collect all the flows
    val flows: Seq[Flow[JsObject, Any, NotUsed]] = definedGenerators.map(_.flow)

    // collect all the post flows
    val postFlows: Seq[Any => Option[Widget]] = definedGenerators.map(
      _.genPostFlow.asInstanceOf[Any => Option[Widget]]
    )

    // zip the flows
    val zippedFlow = AkkaStreamUtil.zipNFlows(flows)

    // zip the post flows
//    val zippedPostFlow =
//      Flow[Seq[Any]].map( flowOutputs =>
//        flowOutputs.zip(postFlows).par.map { case (flowOutput, postFlow) =>
//          postFlow(flowOutput)
//        }.toList
//      )

    for {
      // create a data source
      source <- repo.findAsStream(
        criteria = criteria,
        projection = flowFieldNames
      )

      // execute the flows (with post flows) on the data source to generate widgets
//      widgets <- source.via(zippedFlow.via(zippedPostFlow)).runWith(Sink.head) // .buffer(100, OverflowStrategy.backpressure)

      flowOutputs <- source.via(zippedFlow).runWith(Sink.head)
    } yield {
      val allWidgets = flowOutputs.zip(postFlows).par.map { case (flowOutput, postFlow) =>
        postFlow(flowOutput)
      }.toList

      val specWidgetsMap = definedGenerators.zip(allWidgets).map { case (generator, widget) =>
        (generator.spec, widget)
      }.toGroupMap

      specGenerators.map { case (spec, generator) =>

        // check if static widgets should be generated
        val widgets = specWidgetsMap.get(spec) match {
          case Some(widgets) =>
            if (widgets.isEmpty)
              Seq(genStaticWidget(spec))
            else
              widgets.toSeq

          case None =>
            Seq(genStaticWidget(spec))
        }

        (spec, widgets)
      }
    }
  }

  private def genStreamedIndividuallyAux(
    widgetSpecs: Traversable[WidgetSpec],
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    widgetFilterSubCriteriaMap: Map[BSONObjectID, Seq[Criterion[Any]]],
    nameFieldMap: Map[String, Field],
    fieldNameSubFilterIdMinMaxes: Map[(String, Option[BSONObjectID]), (Double, Double)]
  ): Future[Traversable[(WidgetSpec, Seq[Option[Widget]])]] = sequence(
    widgetSpecs.par.map { widgetSpec =>
      val newCriteria = criteria ++ getSubCriteria(widgetFilterSubCriteriaMap)(widgetSpec.subFilterId)

      genStreamedAux(
        widgetSpec, repo, newCriteria, nameFieldMap, fieldNameSubFilterIdMinMaxes
      ).map((widgetSpec, _))

    }.toList
  )

  private def genFromFullData(
    widgetSpecs: Traversable[WidgetSpec],
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    widgetFilterSubCriteriaMap: Map[BSONObjectID, Seq[Criterion[Any]]],
    nameFieldMap: Map[String, Field]
  ): Future[Traversable[(WidgetSpec, Seq[Option[Widget]])]] =
    sequence(
      widgetSpecs.groupBy(_.subFilterId).map { case (subFilterId, widgetSpecs) =>
        val fieldNames = widgetSpecs.map(_.fieldNames).flatten.toSet
        val finalCriteria = criteria ++ getSubCriteria(widgetFilterSubCriteriaMap)(subFilterId)

        repo.find(
          criteria = finalCriteria,
          projection = fieldNames
        ).map { jsons =>
          widgetSpecs.par.map { widgetSpec =>
            val widgets = genFromFullDataAux(widgetSpec, jsons, criteria, nameFieldMap)
            (widgetSpec, widgets)
          }.toList
        }
      }
    ).map(_.flatten)

  private def getSubCriteria(
    widgetFilterSubCriteriaMap: Map[BSONObjectID, Seq[Criterion[Any]]])(
    filterId: Option[BSONObjectID]
  ): Seq[Criterion[Any]] =
    filterId.map(subFilterId =>
      widgetFilterSubCriteriaMap.get(subFilterId).getOrElse(Nil)
    ).getOrElse(Nil)

  override def genFromRepo(
    widgetSpec: WidgetSpec,
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    fields: Traversable[Field]
  ): Future[Seq[Option[Widget]]] = {
    val nameFieldMap = fields.map(field => (field.name, field)).toMap
    genFromRepoAux(widgetSpec, repo, criteria, nameFieldMap).map(Seq(_))
  }

  private def genFromRepoAux(
    widgetSpec: WidgetSpec,
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    nameFieldMap: Map[String, Field]
  ): Future[Option[Widget]] = {
    val fields = widgetSpec.fieldNames.flatMap(nameFieldMap.get).toSeq

    // aux functions to retrieve a field by name
    def getField(name: String): Field =
      nameFieldMap.get(name).getOrElse(throw new AdaException(s"Field $name not found."))

    widgetSpec match {

      // distribution

      case spec: DistributionWidgetSpec if spec.groupFieldName.isEmpty && fields(0).isNumeric && !fields(0).isArray =>
        val field = getField(spec.fieldName)
        calcNumericDistributionCountsFromRepo(repo, criteria, field, spec.numericBinCount).map(
          NumericDistributionWidgetGenerator(0, 1).apply(spec)(nameFieldMap)
        )

      case spec: DistributionWidgetSpec if spec.groupFieldName.isEmpty && !fields(0).isNumeric =>
        val field = getField(spec.fieldName)
        calcUniqueDistributionCountsFromRepo(repo, criteria, field).map(
          CategoricalDistributionWidgetGenerator(spec)(nameFieldMap)
        )

      case spec: DistributionWidgetSpec if spec.groupFieldName.isDefined && fields(1).isNumeric && !fields(1).isArray =>
        val field = getField(spec.fieldName)
        val groupField = getField(spec.groupFieldName.get)
        calcGroupedNumericDistributionCountsFromRepo(repo, criteria, field, groupField, spec.numericBinCount).map(
          GroupNumericDistributionWidgetGenerator(0, 1)(spec)(nameFieldMap)
        )

      case spec: DistributionWidgetSpec if spec.groupFieldName.isDefined && !fields(1).isNumeric =>
        val field = getField(spec.fieldName)
        val groupField = getField(spec.groupFieldName.get)
        calcGroupedUniqueDistributionCountsFromRepo(repo, criteria, field, groupField).map(
          GroupCategoricalDistributionWidgetGenerator(spec)(nameFieldMap)
        )

      // categorical checkbox count

      case spec: CategoricalCheckboxWidgetSpec if !fields(0).isArray =>
        val field = getField(spec.fieldName)
        calcUniqueDistributionCountsFromRepo(repo, criteria, field).map(
          CategoricalCheckboxCountWidgetGenerator(criteria).apply(spec)(nameFieldMap)
        )

      // cumulative count

      case spec: CumulativeCountWidgetSpec if spec.numericBinCount.isDefined && spec.groupFieldName.isEmpty && fields(0).isNumeric && !fields(0).isArray =>
        val field = getField(spec.fieldName)
        calcNumericDistributionCountsFromRepo(repo, criteria, field, spec.numericBinCount).map( counts =>
          CumulativeNumericBinCountWidgetGenerator(0, 1)(spec)(nameFieldMap)(toCumCounts(counts))
        )

      case spec: CumulativeCountWidgetSpec if spec.numericBinCount.isDefined && spec.groupFieldName.isEmpty && !fields(0).isNumeric =>
        val field = getField(spec.fieldName)
        calcUniqueDistributionCountsFromRepo(repo, criteria, field).map(
          UniqueCumulativeCountWidgetGenerator(spec)(nameFieldMap)
        )

      case spec: CumulativeCountWidgetSpec if spec.numericBinCount.isDefined && spec.groupFieldName.isDefined && fields(1).isNumeric && !fields(1).isArray =>
        val field = getField(spec.fieldName)
        val groupField = getField(spec.groupFieldName.get)
        calcGroupedNumericDistributionCountsFromRepo(repo, criteria, field, groupField, spec.numericBinCount).map( groupCounts =>
          GroupCumulativeNumericBinCountWidgetGenerator(0, 1)(spec)(nameFieldMap)(groupCounts.map { case (group, counts) => (group, toCumCounts(counts))})
        )

      case spec: CumulativeCountWidgetSpec if spec.numericBinCount.isDefined && spec.groupFieldName.isDefined && !fields(1).isNumeric =>
        val field = getField(spec.fieldName)
        val groupField = getField(spec.groupFieldName.get)
        calcGroupedUniqueDistributionCountsFromRepo(repo, criteria, field, groupField).map(
          GroupUniqueCumulativeCountWidgetGenerator(spec)(nameFieldMap)
        )

      // box plot

      case spec: BoxWidgetSpec if spec.groupFieldName.isEmpty =>
        val field = getField(spec.fieldName)
        calcQuartilesFromRepo(repo, criteria, field).map(
          BoxWidgetGenerator(spec)(nameFieldMap)
        )

      case spec: BoxWidgetSpec if spec.groupFieldName.isDefined =>
        val field = getField(spec.fieldName)
        val groupField = getField(spec.groupFieldName.get)
        calcGroupQuartilesFromRepo(repo, criteria, field, groupField).map(
          GroupBoxWidgetGenerator(spec)(nameFieldMap)
        )

      // grid distribution count

      case spec: GridDistributionCountWidgetSpec =>
        calcSeqNumericDistributionCountsFromRepo(
          repo, criteria, Seq((getField(spec.xFieldName), Some(spec.xBinCount)), (getField(spec.yFieldName), Some(spec.yBinCount)))
        ).map(
          GridDistributionCountWidgetGenerator(0, 1, 0, 1)(spec)(nameFieldMap)
        )

      // custom HTML

      case spec: CustomHtmlWidgetSpec =>
        val widget = HtmlWidget("", spec.content, spec.displayOptions)
        Future(Some(widget))

      case _ => Future(None)
    }
  }

  protected def toCumCounts[T](
    counts: Traversable[(BigDecimal, Int)]
  ): Traversable[(BigDecimal, Int)] = {
    val countsSeq = counts.toSeq
    val cumCounts = countsSeq.scanLeft(0) { case (sum, (_, count)) => sum + count }
    countsSeq.zip(cumCounts.tail).map { case ((value, _), sum) => (value, sum)}
  }

  override def genFromFullData(
    widgetSpec: WidgetSpec,
    items: Traversable[JsObject],
    criteria: Seq[Criterion[Any]],
    fields: Traversable[Field]
  ): Seq[Option[Widget]] = {
    val nameFieldMap = fields.map(field => (field.name, field)).toMap
    genFromFullDataAux(widgetSpec, items, criteria, nameFieldMap)
  }

  private def genFromFullDataAux(
    widgetSpec: WidgetSpec,
    jsons: Traversable[JsObject],
    criteria: Seq[Criterion[Any]],
    nameFieldMap: Map[String, Field]
  ): Seq[Option[Widget]] = {
    val fields = widgetSpec.fieldNames.flatMap(nameFieldMap.get).toSeq

    // we don't use streaming (flows) here hence we can feed any min/max flow option values
    val dummyFlowMinMaxMap = fields.map( field => ((field.name, widgetSpec.subFilterId) -> (0d, 1d))).toMap

    val generators = createGenerator(widgetSpec, criteria, nameFieldMap, dummyFlowMinMaxMap)

    generators match {
      case Nil => Seq(genStaticWidget(widgetSpec))
      case _ => generators.map(_.genJson(jsons))
    }
  }

  override def genStreamed(
    widgetSpec: WidgetSpec,
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    fields: Traversable[Field],
    widgetFilterSubCriteriaMap: Map[BSONObjectID, Seq[Criterion[Any]]]
  ): Future[Seq[Option[Widget]]] = {
    val nameFieldMap = fields.map(field => (field.name, field)).toMap
    val subFilterId = widgetSpec.subFilterId
    val finalCriteria = criteria ++ getSubCriteria(widgetFilterSubCriteriaMap)(subFilterId)

    for {
      // retrieve min and max values if requested by a widget
      fieldNameMinMaxes <- sequence(
        flowMinMaxFields(widgetSpec, nameFieldMap).map { field =>

          getNumericMinMax(repo, finalCriteria, field).map { case (minOption, maxOption) =>
            minOption.zip(maxOption).headOption.map { case (min, max) =>
              Some((field.name, min, max))
            }.getOrElse {
              // otherwise return none and log a warning message
              logger.warn(s"Cannot generate a widget of type ${widgetSpec.getClass.getName} because its field ${field.name} has no min/max values. Probably it doesn't contain any values or all are null.")
              None
            }
          }
        }
      )

      widgets <-
        if (fieldNameMinMaxes.contains(None)) {
          Future(Nil)
        } else {
          // if min and max values are available, create a map and generate a streamed widget
          val fieldNameMinMaxMap = fieldNameMinMaxes.collect { case Some(x) => x }.map { case (fieldName, min, max) =>
            (fieldName, subFilterId) -> (min, max)
          }.toMap

          genStreamedAux(widgetSpec, repo, criteria, nameFieldMap, fieldNameMinMaxMap)
        }
    } yield
      widgets
  }

  private def genStreamedAux(
    widgetSpec: WidgetSpec,
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    nameFieldMap: Map[String, Field],
    fieldNameSubFilterIdMinMaxes: Map[(String, Option[BSONObjectID]), (Double, Double)]
  ): Future[Seq[Option[Widget]]] = {
    val generators = createGenerator(widgetSpec, criteria, nameFieldMap, fieldNameSubFilterIdMinMaxes)

    generators match {
      case Nil => Future(Seq(genStaticWidget(widgetSpec)))
      case _ => sequence(
        generators.map(_.genJsonRepoStreamed(repo, criteria))
      )
    }
  }

  private def genStaticWidget(widgetSpec: WidgetSpec) =
    widgetSpec match {
      case spec: CustomHtmlWidgetSpec =>
        val widget = HtmlWidget("", spec.content, spec.displayOptions)
        Some(widget)

      case _ => None
    }

  private def createGenerator(
    widgetSpec: WidgetSpec,
    criteria: Seq[Criterion[Any]],
    nameFieldMap: Map[String, Field],
    fieldNameSubFilterIdMinMaxes: Map[(String, Option[BSONObjectID]), (Double, Double)]
  ): Seq[CalculatorWidgetGeneratorLoaded[_, Widget,_]] = {
    val fields = widgetSpec.fieldNames.map(nameFieldMap.get).toSeq

    if (fields.exists(_.isEmpty)) {
      logger.warn(s"Cannot generate a widget ${widgetSpec.getClass.getSimpleName} because some of its fields do not exist : ${widgetSpec.fieldNames.mkString(", ")}.")
      Nil
    } else
      createGenerator(widgetSpec, fields.flatten, criteria, nameFieldMap, fieldNameSubFilterIdMinMaxes)
  }

  private def createGenerator(
    widgetSpec: WidgetSpec,
    fields: Seq[Field],
    criteria: Seq[Criterion[Any]],
    nameFieldMap: Map[String, Field],
    fieldNameSubFilterIdMinMaxes: Map[(String, Option[BSONObjectID]), (Double, Double)]
  ): Seq[CalculatorWidgetGeneratorLoaded[_, Widget,_]] = {
    def minMaxes = flowMinMaxes(widgetSpec, nameFieldMap, fieldNameSubFilterIdMinMaxes)
    def minMax = minMaxes.head

    def aux[S <: WidgetSpec, W <: Widget, C <: CalculatorTypePack](
      generator: CalculatorWidgetGenerator[S, W, C]
    ) =
      Seq(CalculatorWidgetGeneratorLoaded[S, W, C](generator, widgetSpec.asInstanceOf[S], fields))

    widgetSpec match {

      // distribution

      case spec: DistributionWidgetSpec if spec.groupFieldName.isEmpty && !fields(0).isNumeric =>
        aux(CategoricalDistributionWidgetGenerator)

      case spec: DistributionWidgetSpec if spec.groupFieldName.isEmpty && (fields(0).isDouble || fields(0).isDate || (fields(0).isInteger && spec.numericBinCount.isDefined)) =>
        aux(NumericDistributionWidgetGenerator(minMax))

      case spec: DistributionWidgetSpec if spec.groupFieldName.isEmpty && fields(0).isInteger && spec.numericBinCount.isEmpty =>
        aux(UniqueIntDistributionWidgetGenerator)

      case spec: DistributionWidgetSpec if spec.groupFieldName.isDefined && !fields(1).isNumeric =>
        aux(GroupCategoricalDistributionWidgetGenerator)

      case spec: DistributionWidgetSpec if spec.groupFieldName.isDefined && (fields(1).isDouble || fields(1).isDate || (fields(1).isInteger && spec.numericBinCount.isDefined)) =>
        aux(GroupNumericDistributionWidgetGenerator(minMax))

      case spec: DistributionWidgetSpec if spec.groupFieldName.isDefined && fields(1).isInteger && spec.numericBinCount.isEmpty =>
        aux(GroupUniqueIntDistributionWidgetGenerator)

      // categorical checkbox count

      case spec: CategoricalCheckboxWidgetSpec =>
        aux(CategoricalCheckboxCountWidgetGenerator(criteria))

      // cumulative count

      case spec: CumulativeCountWidgetSpec if spec.numericBinCount.isDefined && spec.groupFieldName.isEmpty =>
        aux(CumulativeNumericBinCountWidgetGenerator(minMax))

      case spec: CumulativeCountWidgetSpec if spec.numericBinCount.isDefined && spec.groupFieldName.isDefined =>
        aux(GroupCumulativeNumericBinCountWidgetGenerator(minMax))

      case spec: CumulativeCountWidgetSpec if spec.numericBinCount.isEmpty && spec.groupFieldName.isEmpty =>
        aux(CumulativeCountWidgetGenerator)

      case spec: CumulativeCountWidgetSpec if spec.numericBinCount.isEmpty && spec.groupFieldName.isDefined =>
        aux(GroupCumulativeCountWidgetGenerator)

      // box plot

      case spec: BoxWidgetSpec if spec.groupFieldName.isEmpty =>
        aux(BoxWidgetGenerator)

      case spec: BoxWidgetSpec if spec.groupFieldName.isDefined =>
        aux(GroupBoxWidgetGenerator)

      // scatter

      case spec: ScatterWidgetSpec if spec.groupFieldName.isEmpty =>
        aux(ScatterWidgetGenerator[Any, Any])

      case spec: ScatterWidgetSpec if spec.groupFieldName.isDefined =>
        aux(GroupScatterWidgetGenerator[Any, Any])

      case _: ValueScatterWidgetSpec =>
        aux(ValueScatterWidgetGenerator[Any, Any, Any])

      // heatmap aggregation

      case spec: HeatmapAggWidgetSpec =>
        val minMaxValues = minMaxes
        aux(HeatmapAggWidgetGenerator.apply(spec.aggType, minMaxValues(0), minMaxValues(1)))

      // grid distribution count

      case _: GridDistributionCountWidgetSpec =>
        val minMaxValues = minMaxes
        aux(GridDistributionCountWidgetGenerator(minMaxValues(0), minMaxValues(1)))

      // correlation

      case spec: CorrelationWidgetSpec if spec.correlationType == CorrelationType.Pearson =>
        aux(PearsonCorrelationWidgetGenerator(Some(streamedCorrelationCalcParallelism)))

      case spec: CorrelationWidgetSpec if spec.correlationType == CorrelationType.Matthews =>
        aux(MatthewsCorrelationWidgetGenerator(Some(streamedCorrelationCalcParallelism)))

      // basic stats

      case _: BasicStatsWidgetSpec =>
        aux(BasicStatsWidgetGenerator)

      // independence test

      case spec: IndependenceTestWidgetSpec if spec.keepUndefined =>
        aux(ChiSquareTestWidgetGenerator) ++ aux(OneWayAnovaTestWidgetGenerator)

      case spec: IndependenceTestWidgetSpec if !spec.keepUndefined =>
        aux(NullExcludedChiSquareTestWidgetGenerator) ++ aux(NullExcludedOneWayAnovaTestWidgetGenerator)

      case _ => Nil
    }
  }

  private def flowMinMaxes(
    widgetSpec: WidgetSpec,
    nameFieldMap: Map[String, Field],
    fieldNameSubFilterIdMinMaxes: Map[(String, Option[BSONObjectID]), (Double, Double)]
  ): Seq[(Double, Double)] = {
    flowMinMaxFields(widgetSpec, nameFieldMap).map { field =>
      val fieldName = field.name
      val subFilterId = widgetSpec.subFilterId
      fieldNameSubFilterIdMinMaxes.get((fieldName, subFilterId)).getOrElse(
        throw new AdaException(s"Min max values for field $fieldName requested by a widget ${widgetSpec.getClass.getName} is not available.")
      )
    }
  }

  private def flowMinMaxFields(
    widgetSpec: WidgetSpec,
    nameFieldMap: Map[String, Field]
  ): Seq[Field] = {
    val fields = widgetSpec.fieldNames.flatMap(nameFieldMap.get).toSeq

    widgetSpec match {

      case spec: DistributionWidgetSpec if spec.groupFieldName.isEmpty && (fields(0).isDouble || fields(0).isDate || (fields(0).isInteger && spec.numericBinCount.isDefined)) =>
        Seq(fields(0))

      case spec: DistributionWidgetSpec if spec.groupFieldName.isDefined && (fields(1).isDouble || fields(1).isDate || (fields(1).isInteger && spec.numericBinCount.isDefined)) =>
        Seq(fields(1))

      case spec: CumulativeCountWidgetSpec if spec.numericBinCount.isDefined && spec.groupFieldName.isEmpty =>
        Seq(fields(0))

      case spec: CumulativeCountWidgetSpec if spec.numericBinCount.isDefined && spec.groupFieldName.isDefined =>
        Seq(fields(1))

      case spec: HeatmapAggWidgetSpec =>
        Seq(fields(0), fields(1))

      case spec: GridDistributionCountWidgetSpec =>
        Seq(fields(0), fields(1))

      case _ => Nil
    }
  }
}