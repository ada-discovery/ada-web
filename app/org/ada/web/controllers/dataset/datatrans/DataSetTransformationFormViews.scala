package org.ada.web.controllers.dataset.datatrans

import java.util.Date

import org.ada.server.dataaccess.StreamSpec
import org.ada.server.models._
import org.ada.server.models.datatrans.{ChangeFieldEnumsTransformation, DataSetTransformation, ResultDataSetSpec}
import org.ada.web.controllers.DataSetControllerActionNames
import org.ada.web.controllers.core.GenericMapping
import org.ada.web.util.toHumanReadableCamel
import org.incal.core.util.{firstCharToLowerCase, hasNonAlphanumericUnderscore}
import org.incal.play.controllers.{CreateEditFormViews, IdForm, WebContext}
import org.incal.play.formatters.{EnumFormatter, MapJsonFormatter, SeqFormatter}
import play.api.data.Forms._
import play.api.data.{Form, Mapping}
import play.twirl.api.Html
import reactivemongo.bson.BSONObjectID
import views.html.layout
import views.html.{datasettrans => view}

import scala.collection.Traversable
import scala.reflect.runtime.universe.{TypeTag, typeOf}

abstract protected[controllers] class DataSetTransformationFormViews[E <: DataSetTransformation: TypeTag](
  implicit manifest: Manifest[E]
) extends CreateEditFormViews[E, BSONObjectID] {

  protected[controllers] val displayName: String = toHumanReadableCamel(simpleClassName)
  private val className =  manifest.runtimeClass.getName

  protected implicit val seqFormatter = SeqFormatter.apply
  private implicit val mapFormatter = MapJsonFormatter.apply
  private implicit val filterShowFieldStyleFormatter = EnumFormatter(FilterShowFieldStyle)
  private implicit val storageTypeFormatter = EnumFormatter(StorageType)
  private implicit val widgetGenerationMethodFormatter = EnumFormatter(WidgetGenerationMethod)

  // Basic Forms

  protected val scheduledTimeMapping: Mapping[ScheduledTime] = mapping(
    "hour" -> optional(number(min = 0, max = 23)),
    "minute" -> optional(number(min = 0, max = 59)),
    "second" -> optional(number(min = 0, max = 59))
  )(ScheduledTime.apply)(ScheduledTime.unapply)

  protected val dataSetIdMapping = nonEmptyText.verifying(
    "Data Set Id must not contain any non-alphanumeric characters (except underscore)",
    dataSetId => !hasNonAlphanumericUnderscore(dataSetId.replaceFirst("\\.",""))
  )

  protected val resultDataSetSpecMapping: Mapping[ResultDataSetSpec] = mapping(
    "id" -> dataSetIdMapping,
    "name" -> nonEmptyText,
    "storageType" -> of[StorageType.Value]
  )(ResultDataSetSpec.apply)(ResultDataSetSpec.unapply)

  protected val streamSpecMapping: Mapping[StreamSpec] = mapping(
    "batchSize" -> optional(number(min = 1, max = 1000)),
    "backpressureBufferSize" -> optional(number(min = 1, max = 1000)),
    "parallelism" -> optional(number(min = 1, max = 100))
  )(StreamSpec.apply)(StreamSpec.unapply)

  protected val extraMappings: Traversable[(String, Mapping[_])] = Nil

  protected[controllers] lazy val form: Form[E] = Form(
    GenericMapping.applyCaseClass[E](
      typeOf[E],
      Seq(
        "_id" -> ignored(Option.empty[BSONObjectID]),
        "resultDataSetSpec" -> resultDataSetSpecMapping,
        "streamSpec" -> streamSpecMapping,
        "scheduled" -> boolean,
        "scheduledTime" -> optional(scheduledTimeMapping),
        "timeCreated" -> default(date("yyyy-MM-dd HH:mm:ss"), new Date()),
        "timeLastExecuted" -> optional(date("yyyy-MM-dd HH:mm:ss"))
      ) ++ extraMappings
    ).verifying(
      "Transformation is marked as 'scheduled' but no time provided",
      transformationInfo => (!transformationInfo.scheduled) || (transformationInfo.scheduledTime.isDefined)
    )
  )

  protected val viewElements: (Form[E], WebContext) => Html

  protected def editViews(form: Form[E])(implicit webContext: WebContext) =
    view.edit(form, className)(viewElements(form, webContext))(webContext.msg)

  protected val defaultCreateInstance: Option[() => E] = None

  override protected[controllers] def fillForm(item: E) =
    form.fill(item)

  override protected def createView = { implicit ctx: WebContext =>
    form: CreateViewData =>
      val filledForm = if (form.hasErrors) form else defaultCreateInstance.map(x => form.fill(x())).getOrElse(form)

      layout.create(
        displayName,
        messagePrefix,
        filledForm,
        editViews(filledForm),
        routes.DataSetTransformationController.save,
        routes.DataSetTransformationController.listAll()
      )
  }

  override protected def editView = { implicit ctx: WebContext =>
    data: IdForm[BSONObjectID, E] =>
      layout.edit(
        displayName,
        messagePrefix,
        data.form.errors,
        editViews(data.form),
        routes.DataSetTransformationController.update(data.id),
        routes.DataSetTransformationController.listAll(),
        Some(routes.DataSetTransformationController.delete(data.id))
      )
  }
}