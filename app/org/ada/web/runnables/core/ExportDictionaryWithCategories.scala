package org.ada.web.runnables.core

import org.ada.web.runnables.RunnableFileOutput
import org.apache.commons.lang3.StringEscapeUtils

import scala.concurrent.ExecutionContext.Implicits.global
import runnables.DsaInputFutureRunnable

class ExportDictionaryWithCategories extends DsaInputFutureRunnable[ExportDictionaryWithCategoriesSpec] with RunnableFileOutput {

  override def runAsFuture(input: ExportDictionaryWithCategoriesSpec) = {
    val dsa = createDsa(input.dataSetId)
    val unescapedDelimiter = StringEscapeUtils.unescapeJava(input.delimiter)

    for {
      // get the fields
      fields <- dsa.fieldRepo.find()

      // get the categories
      categories <- dsa.categoryRepo.find()
    } yield {
      val idCategoryMap = categories.map(cat => (cat._id.get, cat)).toMap

      val fieldAndCatsSorted = fields.map { field =>
        val category = field.categoryId.flatMap(idCategoryMap.get)
        (field, category.map(_.name).getOrElse(""), category.flatMap(_.label).getOrElse(""))
      }.toSeq.sortWith { case ((field1, catName1, _), (field2, catName2, _)) =>
        if (catName1 == catName2)
          field1.name < field2.name
        else
          catName1 < catName2
      }

      // collect all the lines
      val lines = fieldAndCatsSorted.map { case (field, catName, catLabel) =>
        val fieldLabel = field.label.getOrElse("").replaceAllLiterally("\n", " ").replaceAllLiterally("\r", " ")

        Seq(
          catName, catLabel, field.name, fieldLabel, field.fieldType.toString
        ).mkString(unescapedDelimiter)
      }

      // create a header
      val header = Seq("category_name", "category_label" ,"field_name", "field_label", "field_type").mkString(unescapedDelimiter)

      // write to file
      (header +: lines).foreach(addOutputLine)
      setOutputFileName(s"${timestamp}_${input.dataSetId}_dictionary.csv")
    }
  }
}

case class ExportDictionaryWithCategoriesSpec(
  dataSetId: String,
  delimiter: String
)
