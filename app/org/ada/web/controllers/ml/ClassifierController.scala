package org.ada.web.controllers.ml

import java.util.Date
import javax.inject.Inject

import org.ada.web.controllers._
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.server.models.DataSpaceMetaInfo
import org.ada.server.models.ml.classification.Classifier._
import org.ada.server.dataaccess.RepoTypes._
import play.api.data.Forms.{mapping, optional, _}
import play.api.data.format.Formats._
import play.api.data.{Form, Mapping}
import play.api.i18n.Messages
import play.api.libs.json.{JsArray, Json}
import play.twirl.api.Html
import reactivemongo.play.json.BSONFormats._
import reactivemongo.bson.BSONObjectID
import org.ada.web.services.DataSpaceService
import org.ada.web.controllers.ml.{routes => routes}
import views.html.{layout, classification => view}
import org.incal.spark_ml.models.ValueOrSeq.ValueOrSeq
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.AscSort
import org.incal.core.util.firstCharToLowerCase
import org.incal.play.Page
import org.incal.play.controllers._
import org.incal.play.formatters._
import org.incal.spark_ml.models.TreeCore
import org.incal.spark_ml.models.classification._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ClassifierController @Inject()(
    repo: ClassifierRepo,
    dataSpaceService: DataSpaceService
  ) extends AdaCrudControllerImpl[Classifier, BSONObjectID](repo)
    with AdminRestrictedCrudController[BSONObjectID]
    with HasCreateEditSubTypeFormViews[Classifier, BSONObjectID]
    with HasFormShowEqualEditView[Classifier, BSONObjectID] {

  private implicit val logisticModelFamilyFormatter = EnumFormatter(LogisticModelFamily)
  private implicit val mlpSolverFormatter = EnumFormatter(MLPSolver)
  private implicit val decisionTreeImpurityFormatter = EnumFormatter(DecisionTreeImpurity)
  private implicit val featureSubsetStrategyFormatter = EnumFormatter(RandomForestFeatureSubsetStrategy)
  private implicit val gbtClassificationLossTypeFormatter = EnumFormatter(GBTClassificationLossType)
  private implicit val bayesModelTypeFormatter = EnumFormatter(BayesModelType)

  private implicit val intSeqFormatter = SeqFormatter.asInt
  private implicit val doubleSeqFormatter = SeqFormatter.asDouble

  private implicit val intEitherSeqFormatter = EitherSeqFormatter[Int]
  private implicit val doubleEitherSeqFormatter = EitherSeqFormatter[Double]

  protected val treeCoreMapping: Mapping[TreeCore] = mapping(
    "maxDepth" -> of[ValueOrSeq[Int]],
    "maxBins" -> of[ValueOrSeq[Int]],
    "minInstancesPerNode" -> of[ValueOrSeq[Int]],
    "minInfoGain" -> of[ValueOrSeq[Double]],
    "seed" -> optional(longNumber(min = 1))
  )(TreeCore.apply)(TreeCore.unapply)

  protected val logisticRegressionForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "regularization" -> of[ValueOrSeq[Double]], // optional((doubleFormat)),
      "elasticMixingRatio" -> of[ValueOrSeq[Double]],
      "maxIteration" -> of[ValueOrSeq[Int]],
      "tolerance" -> of[ValueOrSeq[Double]],
      "fitIntercept" -> optional(boolean),
      "family" -> optional(of[LogisticModelFamily.Value]),
      "standardization" -> optional(boolean),
      "aggregationDepth" -> of[ValueOrSeq[Int]],
      "threshold" -> of[ValueOrSeq[Double]],
      "thresholds" -> optional(of[Seq[Double]]),
      "name" -> optional(nonEmptyText),
      "createdById" -> ignored(Option.empty[BSONObjectID]),
      "timeCreated" -> ignored(new Date())
    )(LogisticRegression.apply)(LogisticRegression.unapply))

  protected val multiLayerPerceptronForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "hiddenLayers" -> of[Seq[Int]],
      "maxIteration" -> of[ValueOrSeq[Int]],
      "tolerance" -> of[ValueOrSeq[Double]],
      "blockSize" -> of[ValueOrSeq[Int]],
      "solver" -> optional(of[MLPSolver.Value]),
      "seed" -> optional(longNumber(min = 1)),
      "stepSize" -> of[ValueOrSeq[Double]],
      "name" -> optional(nonEmptyText),
      "createdById" -> ignored(Option.empty[BSONObjectID]),
      "timeCreated" -> ignored(new Date())
    )(MultiLayerPerceptron.apply)(MultiLayerPerceptron.unapply))

  protected val decisionTreeForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "core" -> treeCoreMapping,
      "impurity" -> optional(of[DecisionTreeImpurity.Value]),
      "name" -> optional(nonEmptyText),
      "createdById" -> ignored(Option.empty[BSONObjectID]),
      "timeCreated" -> ignored(new Date())
    )(DecisionTree.apply)(DecisionTree.unapply))

  protected val randomForestForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "core" -> treeCoreMapping,
      "numTrees" -> of[ValueOrSeq[Int]],
      "subsamplingRate" -> of[ValueOrSeq[Double]],
      "impurity" -> optional(of[DecisionTreeImpurity.Value]),
      "featureSubsetStrategy" -> optional(of[RandomForestFeatureSubsetStrategy.Value]),
      "name" -> optional(nonEmptyText),
      "createdById" -> ignored(Option.empty[BSONObjectID]),
      "timeCreated" -> ignored(new Date())
    )(RandomForest.apply)(RandomForest.unapply))

  protected val gradientBoostTreeForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "core" -> treeCoreMapping,
      "maxIteration" -> of[ValueOrSeq[Int]],
      "stepSize" -> of[ValueOrSeq[Double]],
      "subsamplingRate" -> of[ValueOrSeq[Double]],
      "lossType" -> optional(of[GBTClassificationLossType.Value]),
      "name" -> optional(nonEmptyText),
      "createdById" -> ignored(Option.empty[BSONObjectID]),
      "timeCreated" -> ignored(new Date())
    )(GradientBoostTree.apply)(GradientBoostTree.unapply))

  protected val naiveBayesForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "smoothing" -> of[ValueOrSeq[Double]],
      "modelType" -> optional(of[BayesModelType.Value]),
      "name" -> optional(nonEmptyText),
      "createdById" -> ignored(Option.empty[BSONObjectID]),
      "timeCreated" -> ignored(new Date())
    )(NaiveBayes.apply)(NaiveBayes.unapply))

  protected val linearSVMForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "aggregationDepth" -> of[ValueOrSeq[Int]],
      "fitIntercept" -> optional(boolean),
      "maxIteration" -> of[ValueOrSeq[Int]],
      "regularization" -> of[ValueOrSeq[Double]],
      "standardization" -> optional(boolean),
      "threshold" -> of[ValueOrSeq[Double]],
      "tolerance" -> of[ValueOrSeq[Double]],
      "name" -> optional(nonEmptyText),
      "createdById" -> ignored(Option.empty[BSONObjectID]),
      "timeCreated" -> ignored(new Date())
    )(LinearSupportVectorMachine.apply)(LinearSupportVectorMachine.unapply))

  protected case class ClassificationCreateEditViews[E <: Classifier](
    displayName: String,
    val form: Form[E],
    viewElements: (Form[E], Messages) => Html)(
    implicit manifest: Manifest[E]
  ) extends CreateEditFormViews[E, BSONObjectID] {

    override protected[controllers] def fillForm(item: E) =
      form.fill(item)

    override protected[controllers] def createView = { implicit ctx =>
      form =>
        layout.create(
          displayName,
          messagePrefix,
          form,
          viewElements(form, ctx.msg),
          routes.ClassifierController.save,
          routes.ClassifierController.listAll(),
          None,
          Seq('enctype -> "multipart/form-data")
        )
    }

    override protected[controllers] def editView = { implicit ctx =>
      data =>
        layout.edit(
          displayName,
          messagePrefix,
          data.form.errors,
          viewElements(data.form, ctx.msg),
          routes.ClassifierController.update(data.id),
          routes.ClassifierController.listAll(),
          Some(routes.ClassifierController.delete(data.id))
        )
    }
  }

  override protected val createEditFormViews =
    Seq(
      ClassificationCreateEditViews[LogisticRegression](
        "Logistic Regression (Classification)",
        logisticRegressionForm,
        view.logisticRegressionElements(_)(_)
      ),

      ClassificationCreateEditViews[MultiLayerPerceptron](
        "MultiLayer Perceptron (Classification)",
        multiLayerPerceptronForm,
        view.multilayerPerceptronElements(_)(_)
      ),

      ClassificationCreateEditViews[DecisionTree](
        "Decision Tree (Classification)",
        decisionTreeForm,
        view.decisionTreeElements(_)(_)
      ),

      ClassificationCreateEditViews[RandomForest](
        "Random Forest (Classification)",
        randomForestForm,
        view.randomForestElements(_)(_)
      ),

      ClassificationCreateEditViews[GradientBoostTree](
        "Gradient Boost Tree (Classification)",
        gradientBoostTreeForm,
        view.gradientBoostTreeElements(_)(_)
      ),

      ClassificationCreateEditViews[NaiveBayes](
        "Naive Bayes (Classification)",
        naiveBayesForm,
        view.naiveBayesElements(_)(_)
      ),

      ClassificationCreateEditViews[LinearSupportVectorMachine](
        "Linear SVM (Classification)",
        linearSVMForm,
        view.linearSupportVectorMachineElements(_)(_)
      )
    )

  override protected val homeCall = routes.ClassifierController.find()

  // default form... unused
  override protected[controllers] val form = logisticRegressionForm.asInstanceOf[Form[Classifier]]

  override def create(concreteClassName: String) = restrictAny(super.create(concreteClassName))

  override protected type ListViewData = (
    Page[Classifier],
    Seq[FilterCondition],
    Traversable[DataSpaceMetaInfo]
  )

  override protected def getListViewData(
    page: Page[Classifier],
    conditions: Seq[FilterCondition]
  ) = { request =>
    for {
      tree <- dataSpaceService.getTreeForCurrentUser(request)
    } yield
      (page, conditions, tree)
  }

  override protected def listView = { implicit ctx => (view.list(_, _, _)).tupled }

  def idAndNames = restrictSubjectPresentAny(noCaching = true) {
    implicit request =>
      for {
        classifications <- repo.find(
          sort = Seq(AscSort("name"))
//          projection = Seq("concreteClass", "name", "timeCreated")
        )
      } yield {
        val idAndNames = classifications.map(classification =>
          Json.obj(
            "_id" -> classification._id,
            "name" -> classification.name
          )
        )
        Ok(JsArray(idAndNames.toSeq))
      }
  }
}