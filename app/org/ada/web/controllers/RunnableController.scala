package org.ada.web.controllers

import javax.inject.Inject
import org.ada.web.controllers.core.{AdaBaseController, GenericMapping}
import org.ada.server.dataaccess.RepoTypes.MessageRepo
import org.ada.server.util.MessageLogger
import org.ada.server.util.ClassFinderUtil.findClasses
import org.incal.play.controllers.{BaseController, WebContext, WithNoCaching}
import play.api.{Configuration, Logger}
import play.api.data.Form
import play.api.mvc.AnyContent
import play.api.mvc.Result
import views.html.{runnable => runnableViews}
import java.{util => ju}

import be.objectify.deadbolt.scala.AuthenticatedRequest
import org.ada.server.field.FieldUtil
import org.ada.web.runnables.{InputView, RunnableFileOutput}
import org.ada.web.util.WebExportUtil
import org.incal.core.util.toHumanReadableCamel
import org.incal.core.runnables._
import org.incal.core.util.ReflectionUtil.currentThreadClassLoader
import org.incal.play.security.SecurityRole
import play.api.inject.Injector
import play.api.libs.json.{JsArray, Json}
import runnables.DsaInputFutureRunnable

import scala.reflect.ClassTag
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RunnableController @Inject() (
  messageRepo: MessageRepo,
  configuration: Configuration,
  injector: Injector
) extends AdaBaseController {

  private val logger = Logger
  private val messageLogger = MessageLogger(logger, messageRepo)

  // we scan only the jars starting with this prefix to speed up the class search
  private val basePackages = Seq(
    "org.ada.server.runnables",
    "org.ada.server.runnables.core",
    "org.ada.web.runnables",
    "org.ada.web.runnables.core",
    "runnables",
    "runnables.core"
  )

  private val packages = basePackages ++ configuration.getStringSeq("runnables.extra_packages").getOrElse(Nil)
  private val searchRunnableSubpackages = configuration.getBoolean("runnables.subpackages.enabled").getOrElse(false)

  private val runnablesHomeRedirect = Redirect(routes.RunnableController.selectRunnable())
  private val appHomeRedirect = Redirect(routes.AppController.index())

  /**
    * Creates view showing all runnables.
    * The view provides an option to launch the runnables and displays feedback once the job is finished.
    *
    * @return View listing all runnables in directory "runnables".
    */
  def selectRunnable = restrictAdminAny(noCaching = true) { implicit request =>
    Future {
      Ok(runnableViews.runnableSelection())
    }
  }

  private def findRunnableNames: Seq[String] = {
    def findAux[T](implicit m: ClassTag[T]) =
      packages.map { packageName =>
        findClasses[T](Some(packageName), !searchRunnableSubpackages)
      }.foldLeft(Stream[Class[T]]()) {
        _ ++ _
      }

    val foundClasses =
      findAux[Runnable] ++
        findAux[InputRunnable[_]] ++
        findAux[InputRunnableExt[_]] ++
        findAux[FutureRunnable] ++
        findAux[InputFutureRunnable[_]] ++
        findAux[InputFutureRunnableExt[_]] ++
        findAux[DsaInputFutureRunnable[_]]

    foundClasses.map(_.getName).sorted
  }

  def runScript(className: String) = scriptActionAux(className) { implicit request =>
    instance =>
      val start = new ju.Date()

      if (instance.isInstanceOf[Runnable]) {
        // plain runnable - execute immediately

        val runnable = instance.asInstanceOf[Runnable]
        runnable.run()

        val execTimeSec = (new java.util.Date().getTime - start.getTime) / 1000
        val message = s"Script '${toShortHumanReadable(className)}' was successfully executed in ${execTimeSec} sec."

        handleRunnableOutput(runnable, message)
      } else {
        // input or input-output runnable needs a form to be filled by a user
        Redirect(routes.RunnableController.getScriptInputForm(className))
      }
  }

  def getScriptInputForm(className: String) = scriptActionAux(className) { implicit request =>
    instance =>

      instance match {
        // input runnable
        case inputRunnable: InputRunnable[_] =>
          val (_, inputFields) = genericInputFormAndFields(inputRunnable)
          Ok(runnableViews.runnableInput(
            className.split('.').last, routes.RunnableController.runInputScript(className), inputFields
          ))

        // plain runnable - no form
        case _ =>
          runnablesHomeRedirect.flashing("errors" -> s"No form available for the script/runnable ${className}.")
    }
  }

  def runInputScript(className: String) = scriptActionAux(className) { implicit request =>
    instance =>
      val start = new ju.Date()

      val inputRunnable = instance.asInstanceOf[InputRunnable[Any]]
      val (form, inputFields) = genericInputFormAndFields(inputRunnable)

      form.bindFromRequest().fold(
        { formWithErrors =>
          BadRequest(runnableViews.runnableInput(
            instance.getClass.getSimpleName, routes.RunnableController.runInputScript(className), inputFields, formWithErrors.errors
          ))
        },
        input => {
          inputRunnable.run(input)

          val execTimeSec = (new java.util.Date().getTime - start.getTime) / 1000
          val message = s"Script '${toShortHumanReadable(className)}' was successfully executed in ${execTimeSec} sec."

          handleRunnableOutput(inputRunnable, message)
        }
      )
  }

  private def genericInputFormAndFields[T](
    inputRunnable: InputRunnable[T])(
    implicit webContext: WebContext
  ) =
    inputRunnable match {
      // input runnable with a custom fields view
      case inputView: InputView[T] =>

        val form = genericForm(inputRunnable)
        val inputFieldsView = inputView.inputFields(implicitly[WebContext])(form.asInstanceOf[Form[T]])

        (form, inputFieldsView)

      // input runnable with a generic fields view
      case _ =>

        val form = genericForm(inputRunnable)

        val nameFieldTypeMap = FieldUtil.caseClassTypeToFlatFieldTypes(inputRunnable.inputType).toMap
        val inputFieldsView = runnableViews.genericFields(form, nameFieldTypeMap)

        (form, inputFieldsView)
    }

  private def genericForm(inputRunnable: InputRunnable[_]): Form[Any] = {
    val mapping = GenericMapping[Any](inputRunnable.inputType)
    Form(mapping)
  }

  private def scriptActionAux(
    className: String)(
    action: AuthenticatedRequest[AnyContent] => Any => Result
  ) = WithNoCaching {
    restrictAdminOrPermissionAny(s"RN:$className") {
      implicit request =>
        for {
          user <- currentUser()
        } yield {
          val isAdmin = user.map(_.isAdmin).getOrElse(false)
          val errorRedirect = if (isAdmin) runnablesHomeRedirect else appHomeRedirect

          try {
            val instance = getInjectedInstance(className)
            action(request)(instance)
          } catch {
            case _: ClassNotFoundException =>
              errorRedirect.flashing("errors" -> s"Script ${className} does not exist.")

            case e: Exception =>
              logger.error(s"Script ${className} failed", e)
              errorRedirect.flashing("errors" -> s"Script ${className} failed due to: ${e.getMessage}")
          }
        }
    }
  }

  private def handleRunnableOutput(
    runnable: Any,
    message: String)(
    implicit request: AuthenticatedRequest[AnyContent]
  ) = {
    messageLogger.info(message)

    runnable match {
      // has a file output
      case fileOutput: RunnableFileOutput =>
        fileOutput.outputByteSource.map ( outputByteSource =>
          WebExportUtil.streamToFile(outputByteSource, fileOutput.fileName)
        ).getOrElse(
          WebExportUtil.stringToFile(fileOutput.output.toString(), fileOutput.fileName)
        )

      // has a HTML output
      case htmlOutput: RunnableHtmlOutput =>

        val output = htmlOutput.output.mkString
        Ok(runnableViews.runnableOutput(runnable.getClass, output, message))

      // no output
      case _ =>
        runnablesHomeRedirect.flashing("success" -> message)
    }
  }

  private def getInjectedInstance(className: String) = {
    val clazz = Class.forName(className, true, currentThreadClassLoader)
    injector.instanceOf(clazz)
  }

  def getRunnableNames = restrictAdminAny(noCaching = true) {
    implicit request => Future {
      val runnableIdAndNames =  findRunnableNames.map { runnableName =>
        Json.obj("name" -> runnableName, "label" -> toShortHumanReadable(runnableName))
      }
      Ok(JsArray(runnableIdAndNames))
    }
  }

  private def toShortHumanReadable(className: String) = {
    val shortName = className.split("\\.", -1).lastOption.getOrElse(className)
    toHumanReadableCamel(shortName)
  }
}