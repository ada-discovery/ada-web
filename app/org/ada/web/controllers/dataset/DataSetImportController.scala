package org.ada.web.controllers.dataset

import java.util.{Date, UUID}

import org.ada.server.services.importers.{DataSetImportScheduler, DataSetImporterCentral}

import scala.concurrent.duration._
import javax.inject.Inject
import org.ada.web.controllers._
import org.ada.server.models.dataimport.DataSetImport.{DataSetImportIdentity, copyWithoutTimestamps, dataSetImportFormat}
import org.ada.server.models._
import org.ada.server.models.dataimport._
import org.ada.server.dataaccess.RepoTypes.{DataSetImportRepo, MessageRepo}
import play.api.{Configuration, Logger}
import play.api.data.{Form, FormError, Mapping}
import play.api.data.Forms.{optional, _}
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsArray, Json}
import play.api.mvc._
import play.twirl.api.Html
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.file.{Files, Paths}

import org.ada.server.services.DataSetService
import org.ada.web.services.DataSpaceService
import org.incal.play.security.SecurityUtil.restrictAdminAnyNoCaching
import views.html.{datasetimport => view}
import views.html.layout
import org.ada.server.util.MessageLogger
import play.api.data.format.Formats._
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.web.controllers.dataset.{routes => routes}
import org.ada.server.{AdaException, AdaParseException}
import org.ada.server.models.{DataSetSetting, DataSpaceMetaInfo, DataView, DistributionWidgetSpec, FilterShowFieldStyle, StorageType, WidgetGenerationMethod}
import org.ada.server.models.DataSetFormattersAndIds.JsObjectIdentity
import org.ada.server.models._
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.AscSort
import org.incal.core.util.{firstCharToLowerCase, hasNonAlphanumericUnderscore, retry}
import org.incal.play.Page
import org.incal.play.controllers._
import org.incal.play.formatters._
import org.incal.play.util.WebUtil.getRequestParamValueOptional

import scala.concurrent.{Await, Future}

class DataSetImportController @Inject()(
    repo: DataSetImportRepo,
    dataSetService: DataSetService,
    dataSetImporterCentral: DataSetImporterCentral,
    dataSetImportScheduler: DataSetImportScheduler,
    dataSpaceService: DataSpaceService,
    messageRepo: MessageRepo

  ) extends AdaCrudControllerImpl[DataSetImport, BSONObjectID](repo)
    with AdminRestrictedCrudController[BSONObjectID]
    with HasCreateEditSubTypeFormViews[DataSetImport, BSONObjectID]
    with HasFormShowEqualEditView[DataSetImport, BSONObjectID] {

  private val logger = Logger
  private val messageLogger = MessageLogger(logger, messageRepo)

  private lazy val importFolder = configuration.getString("datasetimport.import.folder").getOrElse {
    val folder = new java.io.File("dataImports/").getAbsolutePath
    val path = Paths.get(folder)
    // create if doesn't exist
    if (!Files.exists(path)) Files.createDirectory(path)
    folder
  }

  private lazy val importRetryNum = configuration.getInt("datasetimport.retrynum").getOrElse(3)

  // Forms

  protected val scheduledTimeMapping: Mapping[ScheduledTime] = mapping(
    "hour" -> optional(number(min = 0, max = 23)),
    "minute" -> optional(number(min = 0, max = 59)),
    "second" -> optional(number(min = 0, max = 59))
  )(ScheduledTime.apply)(ScheduledTime.unapply)

  private implicit val seqFormatter = SeqFormatter.apply
  private implicit val mapFormatter = MapJsonFormatter.apply
  private implicit val filterShowFieldStyleFormatter = EnumFormatter(FilterShowFieldStyle)
  private implicit val storageTypeFormatter = EnumFormatter(StorageType)
  private implicit val widgetGenerationMethodFormatter = EnumFormatter(WidgetGenerationMethod)

  private val dataSetIdMapping = nonEmptyText.verifying(
    "Data Set Id must not contain any non-alphanumeric characters (except underscore)",
    dataSetId => !hasNonAlphanumericUnderscore(dataSetId.replaceFirst("\\.",""))
  )

  private val dataSetSettingMapping: Mapping[DataSetSetting] = mapping(
    "id" -> ignored(Option.empty[BSONObjectID]),
    "dataSetId" -> nonEmptyText,
    "keyFieldName" -> default(nonEmptyText, JsObjectIdentity.name),
    "exportOrderByFieldName" -> optional(text),
    "defaultScatterXFieldName" -> optional(text),
    "defaultScatterYFieldName" -> optional(text),
    "defaultDistributionFieldName" -> optional(text),
    "defaultCumulativeCountFieldName" -> optional(text),
    "filterShowFieldStyle" -> optional(of[FilterShowFieldStyle.Value]),
    "filterShowNonNullCount" -> boolean,
    "displayItemName" -> optional(text),
    "storageType" -> of[StorageType.Value],
    "mongoAutoCreateIndexForProjection" -> boolean,
    "cacheDataSet" -> ignored(false)
  )(DataSetSetting.apply)(DataSetSetting.unapply)

  private val dataViewMapping: Mapping[DataView] = mapping(
    "tableColumnNames" -> of[Seq[String]],
    "distributionCalcFieldNames" -> of[Seq[String]],
    "elementGridWidth" -> number(min = 1, max = 12),
    "generationMethod" -> of[WidgetGenerationMethod.Value]
  )(DataView.applyMain) { (item: DataView) =>
    Some((
      item.tableColumnNames,
      item.widgetSpecs.collect { case p: DistributionWidgetSpec => p }.map(_.fieldName),
      item.elementGridWidth,
      item.generationMethod
    ))
  }

  protected val csvForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSpaceName" -> nonEmptyText,
      "dataSetId" -> dataSetIdMapping,
      "dataSetName" -> nonEmptyText,
      "path" -> optional(text),
      "delimiter" -> default(nonEmptyText, ","),
      "eol" -> optional(text),
      "charsetName" -> optional(text),
      "matchQuotes" -> boolean,
      "inferFieldTypes" -> boolean,
      "inferenceMaxEnumValuesCount" -> optional(number(min = 1)),
      "inferenceMinAvgValuesPerEnum" -> optional(of[Double]),
      "arrayDelimiter" -> optional(text),
      "booleanIncludeNumbers" -> boolean,
      "saveBatchSize" -> optional(number(min = 1)),
      "scheduled" -> boolean,
      "scheduledTime" -> optional(scheduledTimeMapping),
      "setting" -> optional(dataSetSettingMapping),
      "dataView" -> optional(dataViewMapping),
      "timeCreated" -> default(date("yyyy-MM-dd HH:mm:ss"), new Date()),
      "timeLastExecuted" -> optional(date("yyyy-MM-dd HH:mm:ss"))
    )(CsvDataSetImport.apply)(CsvDataSetImport.unapply)
      .verifying(
        "Import is marked as 'scheduled' but no time provided",
        importInfo => (!importInfo.scheduled) || (importInfo.scheduledTime.isDefined)
      )
  )

  protected val jsonForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSpaceName" -> nonEmptyText,
      "dataSetId" -> dataSetIdMapping,
      "dataSetName" -> nonEmptyText,
      "path" -> optional(text),
      "charsetName" -> optional(text),
      "inferFieldTypes" -> boolean,
      "inferenceMaxEnumValuesCount" -> optional(number(min = 1)),
      "inferenceMinAvgValuesPerEnum" -> optional(of[Double]),
      "booleanIncludeNumbers" -> boolean,
      "saveBatchSize" -> optional(number(min = 1)),
      "scheduled" -> boolean,
      "scheduledTime" -> optional(scheduledTimeMapping),
      "setting" -> optional(dataSetSettingMapping),
      "dataView" -> optional(dataViewMapping),
      "timeCreated" -> default(date("yyyy-MM-dd HH:mm:ss"), new Date()),
      "timeLastExecuted" -> optional(date("yyyy-MM-dd HH:mm:ss"))
    )(JsonDataSetImport.apply)(JsonDataSetImport.unapply)
      .verifying(
        "Import is marked as 'scheduled' but no time provided",
        importInfo => (!importInfo.scheduled) || (importInfo.scheduledTime.isDefined)
      )
  )

  protected val synapseForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSpaceName" -> nonEmptyText,
      "dataSetId" -> dataSetIdMapping,
      "dataSetName" -> nonEmptyText,
      "tableId" -> nonEmptyText,
      "downloadColumnFiles" -> boolean,
      "batchSize" -> optional(number(min = 1)),
      "bulkDownloadGroupNumber" -> optional(number(min = 1)),
      "scheduled" -> boolean,
      "scheduledTime" -> optional(scheduledTimeMapping),
      "setting" -> optional(dataSetSettingMapping),
      "dataView" -> optional(dataViewMapping),
      "timeCreated" -> default(date("yyyy-MM-dd HH:mm:ss"), new Date()),
      "timeLastExecuted" -> optional(date("yyyy-MM-dd HH:mm:ss"))
    )(SynapseDataSetImport.apply)(SynapseDataSetImport.unapply)
      .verifying(
        "Import is marked as 'scheduled' but no time provided",
        importInfo => (!importInfo.scheduled) || (importInfo.scheduledTime.isDefined)
      )
  )

  protected val tranSmartForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSpaceName" -> nonEmptyText,
      "dataSetId" -> dataSetIdMapping,
      "dataSetName" -> nonEmptyText,
      "dataPath" -> optional(text),
      "mappingPath" -> optional(text),
      "charsetName" -> optional(text),
      "matchQuotes" -> boolean,
      "inferFieldTypes" -> boolean,
      "inferenceMaxEnumValuesCount" -> optional(number(min = 1)),
      "inferenceMinAvgValuesPerEnum" -> optional(of[Double]),
      "saveBatchSize" -> optional(number(min = 1)),
      "scheduled" -> boolean,
      "scheduledTime" -> optional(scheduledTimeMapping),
      "setting" -> optional(dataSetSettingMapping),
      "dataView" -> optional(dataViewMapping),
      "timeCreated" -> default(date("yyyy-MM-dd HH:mm:ss"), new Date()),
      "timeLastExecuted" -> optional(date("yyyy-MM-dd HH:mm:ss"))
    )(TranSmartDataSetImport.apply)(TranSmartDataSetImport.unapply)
      .verifying(
        "Import is marked as 'scheduled' but no time provided",
        importInfo => (!importInfo.scheduled) || (importInfo.scheduledTime.isDefined)
      )
  )

  protected val redCapForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSpaceName" -> nonEmptyText,
      "dataSetId" -> dataSetIdMapping,
      "dataSetName" -> nonEmptyText,
      "url" -> nonEmptyText,
      "token" -> nonEmptyText,
      "importDictionaryFlag" -> boolean,
      "eventNames" -> of[Seq[String]],
      "categoriesToInheritFromFirstVisit" -> of[Seq[String]],
      "saveBatchSize" -> optional(number(min = 1)),
      "scheduled" -> boolean,
      "scheduledTime" -> optional(scheduledTimeMapping),
      "setting" -> optional(dataSetSettingMapping),
      "dataView" -> optional(dataViewMapping),
      "timeCreated" -> default(date("yyyy-MM-dd HH:mm:ss"), new Date()),
      "timeLastExecuted" -> optional(date("yyyy-MM-dd HH:mm:ss"))
    )(RedCapDataSetImport.apply)(RedCapDataSetImport.unapply)
      .verifying(
        "Import is marked as 'scheduled' but no time provided",
        importInfo => (!importInfo.scheduled) || (importInfo.scheduledTime.isDefined)
      )
  )

  protected val eGaitForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSpaceName" -> nonEmptyText,
      "dataSetId" -> dataSetIdMapping,
      "dataSetName" -> nonEmptyText,
      "importRawData" -> boolean,
      "scheduled" -> boolean,
      "scheduledTime" -> optional(scheduledTimeMapping),
      "setting" -> optional(dataSetSettingMapping),
      "dataView" -> optional(dataViewMapping),
      "timeCreated" -> default(date("yyyy-MM-dd HH:mm:ss"), new Date()),
      "timeLastExecuted" -> optional(date("yyyy-MM-dd HH:mm:ss"))
    )(EGaitDataSetImport.apply)(EGaitDataSetImport.unapply)
      .verifying(
        "Import is marked as 'scheduled' but no time provided",
        importInfo => (!importInfo.scheduled) || (importInfo.scheduledTime.isDefined)
      )
  )

  protected case class DataSetImportCreateEditViews[E <: DataSetImport](
    displayName: String,
    val form: Form[E],
    viewElements: (Form[E], Messages) => Html,
    defaultCreateInstance: Option[() => E] = None)(
    implicit manifest: Manifest[E]
  ) extends CreateEditFormViews[E, BSONObjectID] {

    private val messagePrefix = firstCharToLowerCase(manifest.runtimeClass.getSimpleName)

    override protected[controllers] def fillForm(item: E) =
      form.fill(item)

    override protected def createView = { implicit ctx: WebContext =>
      form: Form[E] =>
        val filledForm = if (form.hasErrors) form else defaultCreateInstance.map(x => form.fill(x())).getOrElse(form)

        layout.create(
          displayName,
          messagePrefix,
          filledForm,
          viewElements(filledForm, ctx.msg),
          routes.DataSetImportController.save,
          routes.DataSetImportController.listAll(),
          'enctype -> "multipart/form-data"
        )
    }

    override protected def editView = { implicit ctx: WebContext =>
      data: IdForm[BSONObjectID, E] =>
        layout.edit(
          displayName,
          messagePrefix,
          data.form.errors,
          viewElements(data.form, ctx.msg),
          routes.DataSetImportController.update(data.id),
          routes.DataSetImportController.listAll(),
          Some(routes.DataSetImportController.delete(data.id)),
          None,
          None,
          None,
          Seq('enctype -> "multipart/form-data")
        )
    }
  }

  override protected val createEditFormViews =
    Seq(
      DataSetImportCreateEditViews[CsvDataSetImport](
        "CSV Data Set Import",
        csvForm,
        view.csvTypeElements(_)(_),
        Some(() => CsvDataSetImport(
          dataSpaceName = "",
          dataSetId = "",
          dataSetName = "",
          delimiter  = "",
          matchQuotes = false,
          inferFieldTypes = true,
          saveBatchSize = Some(10),
          setting = Some(new DataSetSetting("", StorageType.ElasticSearch))
        ))
      ),

      DataSetImportCreateEditViews[JsonDataSetImport](
        "JSON Data Set Import",
        jsonForm,
        view.jsonTypeElements(_)(_),
        Some(() => JsonDataSetImport(
          dataSpaceName = "",
          dataSetId = "",
          dataSetName = "",
          inferFieldTypes = true,
          saveBatchSize = Some(10),
          setting = Some(new DataSetSetting("", StorageType.ElasticSearch))
        ))
      ),

      DataSetImportCreateEditViews[SynapseDataSetImport](
        "Synapse Data Set Import",
        synapseForm,
        view.synapseTypeElements(_)(_),
        Some(() => SynapseDataSetImport(
          dataSpaceName = "",
          dataSetId = "",
          dataSetName = "",
          tableId = "",
          downloadColumnFiles = false,
          batchSize = Some(10),
          setting = Some(new DataSetSetting("", StorageType.ElasticSearch))
        ))
      ),

      DataSetImportCreateEditViews[TranSmartDataSetImport](
        "TranSMART Data Set (and Dictionary) Import",
        tranSmartForm,
        view.tranSmartTypeElements(_)(_),
        Some(() => TranSmartDataSetImport(
          dataSpaceName = "",
          dataSetId = "",
          dataSetName = "",
          matchQuotes = false,
          inferFieldTypes = true,
          saveBatchSize = Some(10),
          setting = Some(new DataSetSetting("", StorageType.ElasticSearch))
        ))
      ),

      DataSetImportCreateEditViews[RedCapDataSetImport](
        "RedCap Data Set Import",
        redCapForm,
        view.redCapTypeElements(_)(_),
        Some(() => RedCapDataSetImport(
          dataSpaceName = "",
          dataSetId = "",
          dataSetName = "",
          url = "",
          token = "",
          importDictionaryFlag = true,
          saveBatchSize = Some(10),
          setting = Some(new DataSetSetting("", StorageType.ElasticSearch))
        ))
      ),

      DataSetImportCreateEditViews[EGaitDataSetImport](
        "eGait Data Set Import",
        eGaitForm,
        view.eGaitTypeElements(_)(_)
      )
    )

  // default form... unused
  override protected val form = csvForm.asInstanceOf[Form[DataSetImport]]

  override protected val homeCall = routes.DataSetImportController.find()

  override protected type ListViewData = (
    Page[DataSetImport],
    Seq[FilterCondition],
    Traversable[DataSpaceMetaInfo]
  )

  override protected def getListViewData(
    page: Page[DataSetImport],
    conditions: Seq[FilterCondition]
  ) = { request =>
    for {
      tree <- dataSpaceService.getTreeForCurrentUser(request)
    } yield
      (page, conditions, tree)
  }

  override protected def listView = { implicit ctx => (view.list(_, _, _)).tupled}

  def create(concreteClassName: String) = restrictAdminAnyNoCaching(deadbolt) {
    implicit request =>

      getFormWithViews(concreteClassName)
        .createViewWithContextX(implicitly[WebContext])
        .map(Ok(_))
  }

  def execute(id: BSONObjectID) = restrictAdminAnyNoCaching(deadbolt) {
    implicit request =>
      repo.get(id).flatMap(_.fold(
        Future(NotFound(s"Data set import #$id not found"))
      ) { importInfo =>
          val start = new Date()

          implicit val msg = messagesApi.preferred(request)
          val errorMessage = s"Data set '${importInfo.dataSetName}' import failed"

          retry(s"Data set '${importInfo.dataSetName}' import failed: ", logger.warn(_), importRetryNum)(
            dataSetImporterCentral(importInfo)
          ).map { _ =>
            val execTimeSec = (new Date().getTime - start.getTime) / 1000
//            messageLogger.info()
            render {
              case Accepts.Html() => referrerOrHome().flashing("success" -> s"Data set '${importInfo.dataSetName}' has been imported in $execTimeSec sec(s).")
              case Accepts.Json() => Created(Json.obj("message" -> s"Data set has been imported in $execTimeSec sec(s)", "name" -> importInfo.dataSetName))
            }
          }.recover {
            case e: AdaParseException =>
              handleBusinessException(s"$errorMessage. Parsing problem occurred. ${e.getMessage}", e)

            case e: AdaException =>
              handleBusinessException(s"$errorMessage. ${e.getMessage}", e)

            case e: Exception =>
              handleBusinessException(s"$errorMessage. Fatal problem detected. ${e.getMessage}. Contact your admin.", e)
          }
        }
      )
  }

  override protected def formFromRequest(
    implicit request: Request[AnyContent]
  ): Form[DataSetImport] = {
    val filledForm = super.formFromRequest(request)

    // aux function to add param values to a form
    def addToForm(
      form: Form[DataSetImport],
      values: Map[String, String]
    ): Form[DataSetImport] =
      form.bind(form.data ++ values)

    if (!filledForm.hasErrors && filledForm.value.isDefined) {
      val dataSetImport = filledForm.value.get

      // add import file(s) param values and errors
      val extraValuesOrErrors = handleImportFiles(dataSetImport)
      val extraValues = extraValuesOrErrors.collect { case (param, Some(value)) => (param, value) }
      val extraErrors = extraValuesOrErrors.collect { case (param, None) => FormError(param, "error.required", param) }

      extraErrors.foldLeft(addToForm(filledForm, extraValues)){_.withError(_)}
    } else
      filledForm
  }

  override protected def saveCall(
    importInfo: DataSetImport)(
    implicit request: Request[AnyContent]
  ) =
    super.saveCall(importInfo).map { id =>
      scheduleOrCancel(id, importInfo); id
    }

  override protected def updateCall(
    importInfo: DataSetImport)(
    implicit request: Request[AnyContent]
  ) =
    //TODO: remove the old files if any
    super.updateCall(importInfo).map { id =>
      scheduleOrCancel(id, importInfo); id
    }

  def idAndNames = restrictAdminAnyNoCaching(deadbolt) {
    implicit request =>
      for {
        imports <- repo.find(sort = Seq(AscSort("name")))
      } yield {
        val idAndNames = imports.map(dataView =>
          Json.obj(
            "_id" -> dataView._id,
            "name" -> dataView.dataSetId
          )
        )
        Ok(JsArray(idAndNames.toSeq))
      }
  }

  def copy(id: BSONObjectID) = restrictAdminAnyNoCaching(deadbolt) {
    implicit request =>
      repo.get(id).flatMap(_.fold(
        Future(NotFound(s"Entity #$id not found"))
      ) { dataSetImport =>
        val newDataSetImport = copyWithoutTimestamps(DataSetImportIdentity.clear(dataSetImport))

        super.saveCall(newDataSetImport).map { newId =>
          scheduleOrCancel(newId, newDataSetImport)
          Redirect(routes.DataSetImportController.get(newId)).flashing("success" -> s"Data Set import '${dataSetImport.dataSetId}' has been copied.")
        }
      }
    )
  }

  private def handleImportFiles(
    importInfo: DataSetImport)(
    implicit request: Request[AnyContent]
  ): Map[String, Option[String]] = {
    val tempId = BSONObjectID.generate().stringify

    def copyImportFile(name: String, file: File): String = {
      if (new java.io.File(importFolder).exists()) {
        val folderDelimiter = if (importFolder.endsWith("/")) "" else "/"
        val path = importFolder + folderDelimiter + tempId + "/" + name
        copyFile(file, path)
        path
      } else
        throw new AdaException(s"Data set import folder $importFolder does not exist. Create one or override the setting 'datasetimport.import.folder' in custom.conf.")
    }

    def pathKeyValue(
      fileParamKey: String,
      pathParamKey: String)(
      implicit request: Request[AnyContent]
    ): (String, Option[String]) = {
      val path: Option[String] = getFile(fileParamKey, request).map(dataFile =>
        copyImportFile(dataFile._1, dataFile._2)
      ) match {
        case Some(path) => Some(path)
        case None => getRequestParamValueOptional(pathParamKey)
      }
      (pathParamKey, path)
    }

    importInfo match {
      case _: CsvDataSetImport =>
        Seq(pathKeyValue("dataFile", "path")).toMap

      case _: JsonDataSetImport =>
        Seq(pathKeyValue("dataFile", "path")).toMap

      case _: TranSmartDataSetImport =>
        Seq(
          pathKeyValue("dataFile", "dataPath"),
          pathKeyValue("mappingFile", "mappingPath")
        ).toMap

      case _ => Map()
    }
  }

  override protected def deleteCall(id: BSONObjectID)(implicit request: Request[AnyContent]): Future[Unit] =
    super.deleteCall(id).map { _ =>
      dataSetImportScheduler.cancel(id); ()
    }

  private def scheduleOrCancel(id: BSONObjectID, importInfo: DataSetImport): Unit = {
    if (importInfo.scheduled)
      dataSetImportScheduler.schedule(importInfo.scheduledTime.get)(id)
    else
      dataSetImportScheduler.cancel(id)
  }

  private def getFile(fileParamKey: String, request: Request[AnyContent]): Option[(String, java.io.File)] = {
    val dataFileOption = request.body.asMultipartFormData.flatMap(_.file(fileParamKey))
    dataFileOption.flatMap { dataFile =>
      if (dataFile.filename.nonEmpty)
        Some((dataFile.filename, dataFile.ref.file))
      else
        None
    }
  }

  private def copyFile(src: File, location: String): Unit = {
    val dest = new File(location)
    val destFolder = dest.getCanonicalFile.getParentFile
    if (!destFolder.exists()) {
      destFolder.mkdirs()
    }
    new FileOutputStream(dest) getChannel() transferFrom(
      new FileInputStream(src) getChannel, 0, Long.MaxValue )
  }
}