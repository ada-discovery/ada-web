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
import org.incal.core.InputRunnable
import org.ada.server.services.UserManager
import org.ada.web.runnables.RunnableStringOutput

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
  private val libPrefix = "org.ada"
  private val libPath = configuration.getString("lib.path")
  private val showAllRunnables = configuration.getBoolean("admin.runnables.show_all").getOrElse(false)

  private val runnablesHomeRedirect = Redirect(routes.AdminController.listRunnables())
  private val appHomeRedirect = Redirect(routes.AppController.index())

  /**
    * Creates view showing all runnables.
    * The view provides an option to launch the runnables and displays feedback once the job is finished.
    *
    * @return View listing all runnables in directory "runnables".
    */
  def listRunnables = restrictAdminAnyNoCaching(deadbolt) {
    implicit request => Future {

      def findAux[T](packageName: String, fullMatch: Boolean)(implicit m: ClassTag[T]) =
        findClasses[T](libPrefix, Some(packageName), fullMatch, None, libPath)

      val foundClasses =
        if (showAllRunnables) {
          val classes1 = findAux[Runnable]("runnables", false)
          val classes2 = findAux[InputRunnable[_]]("runnables", false)

          classes1 ++ classes2
        } else {
          val classes1 = findAux[Runnable]("runnables", true)
          val classes2 = findAux[InputRunnable[_]]("runnables", true)
          val classes3 = findAux[Runnable]("runnables.core", true)
          val classes4 = findAux[InputRunnable[_]]("runnables.core", true)

          classes1 ++ classes2 ++ classes3 ++ classes4
        }

      val runnableNames = foundClasses.map(_.getName).sorted
      Ok(adminviews.runnables(runnableNames))
    }
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
    if (runnable.isInstanceOf[RunnableStringOutput]) {
      val output = runnable.asInstanceOf[RunnableStringOutput].output.mkString

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
}