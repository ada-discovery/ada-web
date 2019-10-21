package scala.org.ada.server.services.importers

import com.google.inject.Injector
import net.codingwell.scalaguice.InjectorExtensions._
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.models.{DataSetSetting, StorageType}
import org.ada.server.models.dataimport.CsvDataSetImport
import org.ada.server.services.GuicePlayTestApp
import org.ada.server.services.ServiceTypes.DataSetCentralImporter
import org.scalatest._

import scala.io.Codec

class CsvImporterSpec extends AsyncFlatSpec {

  implicit override def executionContext = scala.concurrent.ExecutionContext.Implicits.global
  private implicit val codec = Codec.UTF8

  private val guiceInjector = GuicePlayTestApp().injector.instanceOf[Injector]
  private val importer = guiceInjector.instance[DataSetCentralImporter]
  private val dsaf = guiceInjector.instance[DataSetAccessorFactory]

  private object Iris {
    val path = getClass.getResource("/iris.csv").getPath
    val id = "test.iris"
    val name = "iris"
    val size = 150
    def importInfo(storageType: StorageType) = CsvDataSetImport(
      dataSpaceName = "test",
      dataSetName = name,
      dataSetId = id,
      delimiter = ",",
      matchQuotes = false,
      inferFieldTypes = true,
      path = Some(path),
      setting = Some(new DataSetSetting(id, storageType))
    )
  }

  behavior of "CsvDataSetImport"

  it should "import iris.csv to MongoDB" in {
    for {
      _ <- importer(Iris.importInfo(StorageType.Mongo))
      dsa = dsaf(Iris.id).getOrElse(fail(s"Dataset '${Iris.name}' not found in Mongo."))
      _ <- dsa.dataSetName map { name => assert(name == Iris.name) }
      _ <- dsa.dataSetRepo.count() map { count => assert(count == Iris.size)}
    } yield succeed
  }

  it should "import iris.csv to ElasticSearch" in {
    for {
      _ <- importer(Iris.importInfo(StorageType.ElasticSearch))
      dsa = dsaf(Iris.id).getOrElse(fail(s"Dataset '${Iris.name}' not found in Elastic."))
      _ <- dsa.dataSetName map { name => assert(name == Iris.name) }
      _ <- dsa.dataSetRepo.count() map { count => assert(count == Iris.size)}
    } yield succeed
  }
}
