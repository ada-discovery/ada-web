package unit

import com.google.inject.Injector
import org.ada.server.models.dataimport.CsvDataSetImport
import org.ada.server.services.ServiceTypes.DataSetCentralImporter
import net.codingwell.scalaguice.InjectorExtensions._
import org.ada.server.services.GuicePlayTestApp
import org.scalatest._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application

import scala.io.Codec

class CsvImporterSpec extends FlatSpec with GuiceOneAppPerSuite {

  private implicit val codec: Codec = Codec.UTF8
  private val irisCsv = getClass.getResource("/iris.csv").toString

  override def fakeApplication(): Application =
    GuicePlayTestApp()
//    new GuiceApplicationBuilder()
//      .bindings(bind[DataSetCentralImporter].to(classOf[DataSetCentralImporterImpl]))
//      .build()

  private def guiceInjector = app.injector.instanceOf[Injector]

  "CsvDataSetImport" should "run without error" in {
    val importInfo = CsvDataSetImport(
      dataSpaceName = "test",
      dataSetName = "iris",
      dataSetId = "test.iris",
      delimiter = ",",
      matchQuotes = false,
      inferFieldTypes = true,
      path = Some(irisCsv)
    )
    val importer = guiceInjector.instance[DataSetCentralImporter]
    importer(importInfo)
  }
}
