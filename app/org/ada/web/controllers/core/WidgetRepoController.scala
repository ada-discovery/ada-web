package org.ada.web.controllers.core

import org.ada.server.dataaccess.JsonFormatRepoAdapter
import org.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.ada.server.models.{WidgetGenerationMethod, WidgetSpec}
import org.ada.server.dataaccess.CaseClassFieldRepo
import org.ada.web.services.WidgetGenerationService
import org.incal.core.dataaccess.Criterion._
import org.incal.core.dataaccess.{AsyncReadonlyRepo, Criterion}
import org.ada.web.models.Widget
import play.api.libs.json.Format
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe._

trait WidgetRepoController[E] {

  protected def repo: AsyncReadonlyRepo[E, _]
  protected def typeTag: TypeTag[E]
  protected def format: Format[E]

  protected def wgs: WidgetGenerationService

  protected def excludedFieldNames: Traversable[String] = Nil

  protected lazy val fieldCaseClassRepo = CaseClassFieldRepo[E](excludedFieldNames, true)(typeTag)
  protected lazy val jsonCaseClassRepo = JsonFormatRepoAdapter.applyNoId(repo)(format)

  def widgets(
    widgetSpecs: Traversable[WidgetSpec],
    criteria: Seq[Criterion[Any]]
  ) : Future[Traversable[Option[Widget]]] =
    for {
      fields <- {
        val fieldNames = (criteria.map(_.fieldName) ++ widgetSpecs.flatMap(_.fieldNames)).toSet
        fieldCaseClassRepo.find(Seq(FieldIdentity.name #-> fieldNames.toSeq))
      }

      widgets <- wgs(widgetSpecs, jsonCaseClassRepo, criteria, Map(), fields, WidgetGenerationMethod.FullData)
  } yield
      widgets
}
