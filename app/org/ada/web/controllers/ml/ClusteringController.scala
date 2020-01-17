package org.ada.web.controllers.ml

import java.util.Date

import javax.inject.Inject
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.server.models.ml.clustering.Clustering._
import org.incal.spark_ml.models.clustering._
import org.ada.server.dataaccess.RepoTypes._
import play.api.data.Forms.{mapping, optional, _}
import play.api.data.format.Formats._
import play.api.data.{Form, Mapping}
import play.api.i18n.Messages
import play.api.libs.json.{JsArray, Json}
import play.api.mvc.{Action, AnyContent, Request, Result}
import play.twirl.api.Html
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import org.ada.web.services.DataSpaceService
import org.ada.web.controllers.ml.routes.{ClusteringController => clusteringRoutes}
import org.ada.server.models.DataSpaceMetaInfo
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.AscSort
import org.incal.core.util.firstCharToLowerCase
import org.incal.play.Page
import org.incal.play.controllers._
import org.incal.play.formatters._
import views.html.{layout, clustering => view}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ClusteringController @Inject()(
    repo: ClusteringRepo,
    dataSpaceService: DataSpaceService
  ) extends AdaCrudControllerImpl[Clustering, BSONObjectID](repo)
    with AdminRestrictedCrudController[BSONObjectID]
    with HasCreateEditSubTypeFormViews[Clustering, BSONObjectID]
    with HasFormShowEqualEditView[Clustering, BSONObjectID] {

  private implicit val kMeansInitModeFormatter = EnumFormatter(KMeansInitMode)
  private implicit val ldaptimizerFormatter = EnumFormatter(LDAOptimizer)
  private implicit val doubleSeqFormatter = SeqFormatter.asDouble

  protected val kMeansForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "k" -> number(min = 2),
      "maxIteration" -> optional(number(min = 1)),
      "tolerance" -> optional(of(doubleFormat)),
      "seed" -> optional(longNumber(min = 1)),
      "initMode" -> optional(of[KMeansInitMode.Value]),
      "initSteps" -> optional(number(min = 1)),
      "name" -> optional(nonEmptyText),
      "createdById" -> ignored(Option.empty[BSONObjectID]),
      "timeCreated" -> ignored(new Date())
    )(KMeans.apply)(KMeans.unapply))

  protected val ldaForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "k" -> number(min = 2),
      "maxIteration" -> optional(number(min = 1)),
      "seed" -> optional(longNumber(min = 1)),
      "checkpointInterval" -> optional(number(min = 1)),
      "docConcentration" -> optional(of[Seq[Double]]),
      "topicConcentration" -> optional(of(doubleFormat)),
      "optimizer" -> optional(of[LDAOptimizer.Value]),
      "learningOffset" -> optional(of(doubleFormat)),
      "learningDecay" -> optional(of(doubleFormat)),
      "subsamplingRate" -> optional(of(doubleFormat)),
      "optimizeDocConcentration" -> optional(boolean),
      "keepLastCheckpoint" -> optional(boolean),
      "name" -> optional(nonEmptyText),
      "createdById" -> ignored(Option.empty[BSONObjectID]),
      "timeCreated" -> ignored(new Date())
    ) (LDA.apply)(LDA.unapply))

  protected val bisectingKMeansForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "k" -> number(min = 2),
      "maxIteration" -> optional(number(min = 1)),
      "seed" -> optional(longNumber(min = 1)),
      "minDivisibleClusterSize" -> optional(of(doubleFormat)),
      "name" -> optional(nonEmptyText),
      "createdById" -> ignored(Option.empty[BSONObjectID]),
      "timeCreated" -> ignored(new Date())
    )(BisectingKMeans.apply)(BisectingKMeans.unapply))

  protected val gaussianMixtureForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "k" -> number(min = 2),
      "maxIteration" -> optional(number(min = 1)),
      "tolerance" -> optional(of(doubleFormat)),
      "seed" -> optional(longNumber(min = 1)),
      "name" -> optional(nonEmptyText),
      "createdById" -> ignored(Option.empty[BSONObjectID]),
      "timeCreated" -> ignored(new Date())
    )(GaussianMixture.apply)(GaussianMixture.unapply))

  protected case class UnsupervisedLearningCreateEditViews[E <: Clustering](
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
          clusteringRoutes.save,
          clusteringRoutes.listAll(),
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
          clusteringRoutes.update(data.id),
          clusteringRoutes.listAll(),
          Some(clusteringRoutes.delete(data.id))
        )
    }
  }

  override protected val createEditFormViews =
    Seq(
      UnsupervisedLearningCreateEditViews[KMeans](
        "K Means",
        kMeansForm,
        view.kMeansElements(_)(_)
      ),

      UnsupervisedLearningCreateEditViews[LDA](
        "LDA",
        ldaForm,
        view.ldaElements(_)(_)
      ),

      UnsupervisedLearningCreateEditViews[BisectingKMeans](
        "Bisecting K Means",
        bisectingKMeansForm,
        view.bisectingKMeansElements(_)(_)
      ),

      UnsupervisedLearningCreateEditViews[GaussianMixture](
        "Gaussian Mixture",
        gaussianMixtureForm,
        view.gaussianMixtureElements(_)(_)
      )
    )

  override protected val homeCall = routes.ClusteringController.find()

  // default form... unused
  override protected[controllers] val form = kMeansForm.asInstanceOf[Form[Clustering]]

  override def create(concreteClassName: String) = restrictAny(super.create(concreteClassName))

  override protected type ListViewData = (
    Page[Clustering],
    Seq[FilterCondition],
    Traversable[DataSpaceMetaInfo]
  )

  override protected def getListViewData(
    page: Page[Clustering],
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
        regressions <- repo.find(
          sort = Seq(AscSort("name"))
//          projection = Seq("concreteClass", "name", "timeCreated")
        )
      } yield {
        val idAndNames = regressions.map(regression =>
          Json.obj(
            "_id" -> regression._id,
            "name" -> regression.name
          )
        )
        Ok(JsArray(idAndNames.toSeq))
      }
  }
}