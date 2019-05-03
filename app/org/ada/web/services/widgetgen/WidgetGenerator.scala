package org.ada.web.services.widgetgen

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import org.ada.server.models.{Field, WidgetSpec}
import org.ada.server.calc.{CalculatorExecutor, CalculatorExecutors, CalculatorTypePack}
import org.ada.web.models.Widget
import org.incal.core.dataaccess.{AsyncReadonlyRepo, Criterion, NotEqualsNullCriterion}
import play.api.libs.json.JsObject

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait WidgetGenerator[S <: WidgetSpec, +W <: Widget] {

  type IN

  def apply(
    spec: S)(
    fieldNameMap: Map[String, Field]
  ): IN => Option[W]

  def applyFields(
    spec: S)(
    fields: Seq[Field]
  ): IN => Option[W] = apply(spec)(fields.map(field => field.name -> field).toMap)

  protected def title(widgetSpec: WidgetSpec) = widgetSpec.displayOptions.title
}

trait CalculatorWidgetGenerator[S <: WidgetSpec, +W <: Widget, C <: CalculatorTypePack] extends WidgetGenerator[S, W] with CalculatorExecutors {

  type IN = C#OUT

  protected val seqExecutor: CalculatorExecutor[C, Seq[Field]]

  protected def specToOptions: S => C#OPT

  protected def specToFlowOptions: S => C#FLOW_OPT

  protected def specToSinkOptions: S => C#SINK_OPT

  protected val supportArray: Boolean

  protected def withProjection: Boolean = true

  protected def extraStreamCriteria(
    spec: S,
    fields: Seq[Field]
  ): Seq[Criterion[Any]] = Nil

  protected def withNotNull(fields: Seq[Field]): Seq[Criterion[Any]] =
    fields.map(field => NotEqualsNullCriterion(field.name))

  protected def scalarOrArrayField(fields: Seq[Field]): Field =
    if (fields.size > 1) fields(1) else fields(0)

  protected def filterFields(fields: Seq[Field]) = fields

  def genJson(
    spec: S)(
    fields: Seq[Field])(
    jsons: Traversable[JsObject]
  ): Option[W] = {
    val options = specToOptions(spec)
    val filteredFields = filterFields(fields)

    // decide whether to use a pure scalar json executor or a scalar/array one
    val result =
      if (supportArray)
        seqExecutor.execJsonA(options, scalarOrArrayField(filteredFields), filteredFields)(jsons)
      else
        seqExecutor.execJson(options, filteredFields)(jsons)

    // generate widget out of it
    applyFields(spec)(filteredFields)(result)
  }

  def genJsonRepoStreamed(
    spec: S)(
    fields: Seq[Field])(
    dataRepo: AsyncReadonlyRepo[JsObject, _],
    criteria: Seq[Criterion[Any]])(
    implicit materializer: Materializer
  ): Future[Option[W]] = {
    val flowOptions = specToFlowOptions(spec)
    val sinkOptions = specToSinkOptions(spec)
    val filteredFields = filterFields(fields)

    // decide whether to use a pure scalar json streamed executor or a scalar/array one
    val exec = if (supportArray)
      seqExecutor.execJsonRepoStreamedA(flowOptions, sinkOptions, withProjection, scalarOrArrayField(filteredFields), filteredFields)_
    else
      seqExecutor.execJsonRepoStreamed(flowOptions, sinkOptions, withProjection, filteredFields)_

    for {
      // execute on given data repo with criteria
      calcResult <- exec(dataRepo, criteria ++ extraStreamCriteria(spec, filteredFields))
    } yield
      // generate widget out of it
      applyFields(spec)(filteredFields)(calcResult)
  }

  def flow(
    spec: S)(
    fields: Seq[Field]
  ): Flow[JsObject, C#INTER, NotUsed] = {
    val flowOptions = specToFlowOptions(spec)
    val filteredFields = filterFields(fields)

    // decide whether to use a pure scalar flow or a scalar/array one
    if (supportArray)
      seqExecutor.createJsonFlowA(flowOptions, scalarOrArrayField(filteredFields), filteredFields)
    else
      seqExecutor.createJsonFlow(flowOptions, filteredFields)
  }

  def genPostFlow(
    spec: S)(
    fields: Seq[Field])(
    flowOutput: C#INTER
  ): Option[W] = {
    val sinkOptions = specToSinkOptions(spec)
    val filteredFields = filterFields(fields)

    val result = seqExecutor.execPostFlow(sinkOptions)(flowOutput)

    applyFields(spec)(filteredFields)(result)
  }
}

case class CalculatorWidgetGeneratorLoaded[S <: WidgetSpec, +W <: Widget, C <: CalculatorTypePack](
  generator: CalculatorWidgetGenerator[S, W, C],
  spec: S,
  fields: Seq[Field]
) {

  def apply(
    inputs: generator.IN
  ) = generator.applyFields(spec)(fields)(inputs)

  def genJson(
    jsons: Traversable[JsObject]
  ) = generator.genJson(spec)(fields)(jsons)

  def genJsonRepoStreamed(
    dataRepo: AsyncReadonlyRepo[JsObject, _],
    criteria: Seq[Criterion[Any]])(
    implicit materializer: Materializer
  ) = generator.genJsonRepoStreamed(spec)(fields)(dataRepo, criteria)

  def flow = generator.flow(spec)(fields)

  def genPostFlow = generator.genPostFlow(spec)(fields)(_)
}

trait NoOptionsCalculatorWidgetGenerator[S] {
  protected def specToOptions: S => Unit =
    _ => ()

  protected def specToFlowOptions: S => Unit =
    _ => ()

  protected def specToSinkOptions: S => Unit =
    _ => ()
}