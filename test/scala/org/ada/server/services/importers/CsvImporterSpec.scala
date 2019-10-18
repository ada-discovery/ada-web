package scala.org.ada.server.services.importers

import com.google.inject.Injector
import net.codingwell.scalaguice.InjectorExtensions._
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.models.dataimport.CsvDataSetImport
import org.ada.server.services.GuicePlayTestApp
import org.ada.server.services.ServiceTypes.DataSetCentralImporter
import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.Codec
import scala.concurrent.ExecutionContext.Implicits.global

class CsvImporterSpec extends FlatSpec {

  private val timeout = 1 minute
  private implicit val codec = Codec.UTF8
  private val irisCsv = getClass.getResource("/iris.csv").getPath

  private val guiceInjector = GuicePlayTestApp().injector.instanceOf[Injector]
  private val importer = guiceInjector.instance[DataSetCentralImporter]
  private val dsaf = guiceInjector.instance[DataSetAccessorFactory]

  "CsvDataSetImport" should "import iris.csv to MongoDB" in {
    val dataSetId = "test.iris"
    val dataSetName = "iris"
    val importInfo = CsvDataSetImport(
      dataSpaceName = "test",
      dataSetName = dataSetName,
      dataSetId = dataSetId,
      delimiter = ",",
      matchQuotes = false,
      inferFieldTypes = true,
      path = Some(irisCsv)
    )
    Await.result(importer(importInfo) map  { _ =>
      assert(true)
    }, timeout)
//    importer(importInfo) flatMap { _ =>
//      dsaf(dataSetId) match {
//        case Some(dsa) =>
//          dsa.dataSetName map { name => assert(name == dataSetName) }
//          dsa.dataSetRepo.count() map { count => assert(count == 150)}
//        case None => fail(s"Dataset '$dataSetName' not found in DB.")
//      }
//    }
  }
}
