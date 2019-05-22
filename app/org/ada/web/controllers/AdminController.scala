package org.ada.web.controllers

import javax.inject.Inject
import org.ada.web.controllers.core.GenericMapping
import org.ada.server.dataaccess.RepoTypes.MessageRepo
import org.ada.server.util.MessageLogger
import org.ada.server.util.ClassFinderUtil.findClasses
import org.incal.play.security.SecurityUtil.restrictAdminAnyNoCaching
import org.incal.play.controllers.BaseController
import play.api.{Configuration, Logger}
import play.api.Play.current
import play.api.data.Form
import play.api.mvc.AnyContent
import play.api.mvc.Result
import views.html.{admin => adminviews}
import java.{util => ju}

import be.objectify.deadbolt.scala.AuthenticatedRequest
import org.ada.server.field.FieldUtil
import org.incal.core.runnables.{FutureRunnable, InputFutureRunnable, InputRunnable, RunnableHtmlOutput}
import org.ada.server.services.UserManager
import play.api.libs.json.{JsArray, Json}
import runnables.DsaInputFutureRunnable

import scala.reflect.ClassTag
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AdminController @Inject() (
    messageRepo: MessageRepo,
    userManager: UserManager,
    configuration: Configuration
  ) extends BaseController {

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

  private val runnablesHomeRedirect = Redirect(routes.AdminController.selectRunnable())
  private val appHomeRedirect = Redirect(routes.AppController.index())

  /**
    * Creates view showing all runnables.
    * The view provides an option to launch the runnables and displays feedback once the job is finished.
    *
    * @return View listing all runnables in directory "runnables".
    */
  def selectRunnable = restrictAdminAnyNoCaching(deadbolt) {
    implicit request => Future {
      Ok(adminviews.runnableSelection())
    }
  }

  private def findRunnableNames: Seq[String] = {
    def findAux[T](implicit m: ClassTag[T]) =
      packages.map { packageName =>
        findClasses[T](Some(packageName), !searchRunnableSubpackages)
      }.foldLeft(Stream[Class[T]]()){_++_}

    val classes1 = findAux[Runnable]
    val classes2 = findAux[InputRunnable[_]]
    val classes3 = findAux[FutureRunnable]
    val classes4 = findAux[InputFutureRunnable[_]]
    val classes5 = findAux[DsaInputFutureRunnable[_]]

    val foundClasses = classes1 ++ classes2 ++ classes3 ++ classes4 ++ classes5
    foundClasses.map(_.getName).sorted
  }

  def runScript(className: String) = scriptActionAux(className) { implicit request => instance =>
    val start = new ju.Date()

    if (instance.isInstanceOf[Runnable]) {
      // plain runnable - execute immediately

      val runnable = instance.asInstanceOf[Runnable]
      runnable.run()

      val execTimeSec = (new java.util.Date().getTime - start.getTime) / 1000
      val message = s"Script ${className} was successfully executed in ${execTimeSec} sec."

      handleRunnableOutput(runnable, message)
    } else {
      // input or input-output runnable needs a form to be filled by a user
      Redirect(routes.AdminController.getScriptInputForm(className))
    }
  }

  def getScriptInputForm(className: String) = scriptActionAux(className) { implicit request => instance =>
    if (instance.isInstanceOf[InputRunnable[_]]) {
      // input runnable

      val inputRunnable = instance.asInstanceOf[InputRunnable[_]]

      val mapping = GenericMapping[Any](inputRunnable.inputType)
      val nameFieldTypeMap = FieldUtil.caseClassTypeToFlatFieldTypes(inputRunnable.inputType).toMap

      Ok(adminviews.formFieldsInput(
        className.split('.').last, Form(mapping), routes.AdminController.runInputScript(className), nameFieldTypeMap
      ))
    } else {
      // plain runnable - no form

      runnablesHomeRedirect.flashing("errors" -> s"No form available for the script/runnable ${className}.")
    }
  }

  def runInputScript(className: String) = scriptActionAux(className) { implicit request => instance =>
    val start = new ju.Date()

    val inputRunnable = instance.asInstanceOf[InputRunnable[Any]]
    val mapping = GenericMapping[Any](inputRunnable.inputType)
    val nameFieldTypeMap = FieldUtil.caseClassTypeToFlatFieldTypes(inputRunnable.inputType).toMap

    Form(mapping).bindFromRequest().fold(
      { formWithErrors =>
        BadRequest(adminviews.formFieldsInput(
          instance.getClass.getSimpleName, formWithErrors, routes.AdminController.runInputScript(className), nameFieldTypeMap
        ))
      },
      input => {
        inputRunnable.run(input)

        val execTimeSec = (new java.util.Date().getTime - start.getTime) / 1000
        val message = s"Script ${className} was successfully executed in ${execTimeSec} sec."

        handleRunnableOutput(inputRunnable, message)
      }
    )
  }

  private def scriptActionAux(
    className: String)(
    action: AuthenticatedRequest[AnyContent] => Any => Result
  ) = restrictAdminAnyNoCaching(deadbolt) {
    implicit request => Future {
      implicit val msg = messagesApi.preferred(request)

      try {
        val instance = getInjectedInstance(className)

        action(request)(instance)
      } catch {
        case e: ClassNotFoundException =>
          runnablesHomeRedirect.flashing("errors" -> s"Script ${className} does not exist.")

        case e: Exception =>
          logger.error(s"Script ${className} failed", e)
          runnablesHomeRedirect.flashing("errors" -> s"Script ${className} failed due to: ${e.getMessage}")
      }
    }
  }

  private def handleRunnableOutput(
    runnable: Any,
    message: String)(
    implicit request: AuthenticatedRequest[AnyContent]
  ) = {
    messageLogger.info(message)

    // has output
    if (runnable.isInstanceOf[RunnableHtmlOutput]) {
      val output = runnable.asInstanceOf[RunnableHtmlOutput].output.mkString

      Ok(adminviews.runnableOutput(runnable.getClass, output)).flashing("success" -> message)
    } else {
      runnablesHomeRedirect.flashing("success" -> message)
    }
  }

  private def getInjectedInstance(className: String) = {
    val cls = Thread.currentThread().getContextClassLoader()
    val clazz = Class.forName(className, true, cls)
    current.injector.instanceOf(clazz)
  }

  def importLdapUsers = restrictAdminAnyNoCaching(deadbolt) {
    implicit request =>
      userManager.synchronizeRepos.map ( _ =>
        appHomeRedirect.flashing("success" -> "LDAP users successfully imported.")
      )
  }

  def purgeMissingLdapUsers = restrictAdminAnyNoCaching(deadbolt) {
    implicit request =>
      userManager.purgeMissing.map ( _ =>
        appHomeRedirect.flashing("success" -> "Missing users successfully purged.")
      )
  }

  def getRunnableNames = restrictAdminAnyNoCaching(deadbolt) {
    implicit request => Future {
      val runnableIdAndNames =  findRunnableNames.map { runnableName =>
        val shortName = runnableName.split("\\.", -1).lastOption.getOrElse(runnableName)
        Json.obj("name" -> runnableName, "label" -> org.ada.web.util.toHumanReadableCamel(shortName))
      }
      Ok(JsArray(runnableIdAndNames))
    }
  }
}