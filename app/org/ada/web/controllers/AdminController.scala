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
import views.html.{admin => adminviews}
import java.{util => ju}

import org.incal.core.InputRunnable
import org.ada.server.services.UserManager

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

  private val runnablesRedirect = Redirect(routes.AdminController.listRunnables())
  private val mainRedirect = Redirect(routes.AppController.index())

  def runScript(className: String) = restrictAdminAnyNoCaching(deadbolt) {
    implicit request => Future {
      implicit val msg = messagesApi.preferred(request)
      try {
        val cls = Thread.currentThread().getContextClassLoader()
        val clazz = Class.forName(className, true, cls)
        val instance = current.injector.instanceOf(clazz)

        if (instance.isInstanceOf[InputRunnable[_]]) {
           val inputRunnable = instance.asInstanceOf[InputRunnable[_]]
 //          val fields = FieldUtil.caseClassTypeToFlatFieldTypes(inputRunnable.typ)
           val mapping = GenericMapping[Any](inputRunnable.inputType)
           Ok(adminviews.formFieldsInput(
             className.split('.').last, Form(mapping), routes.AdminController.runInputScript(className)
           ))
        } else {
          val start = new ju.Date()
          instance.asInstanceOf[Runnable].run()
          val execTimeSec = (new java.util.Date().getTime - start.getTime) / 1000
          val message = s"Script ${className} was successfully executed in ${execTimeSec} sec."

          messageLogger.info(message)
          runnablesRedirect.flashing("success" -> message)
        }
      } catch {
        case e: ClassNotFoundException =>
          runnablesRedirect.flashing("errors" -> s"Script ${className} does not exist.")

        case e: Exception =>
          logger.error(s"Script ${className} failed", e)
          runnablesRedirect.flashing("errors" -> s"Script ${className} failed due to: ${e.getMessage}")
      }
    }
  }

  def runInputScript(className: String) = restrictAdminAnyNoCaching(deadbolt) {
    implicit request => Future {
      implicit val msg = messagesApi.preferred(request)
      try {
        val cls = Thread.currentThread().getContextClassLoader()
        val clazz = Class.forName(className, true, cls)
        val start = new ju.Date()
        val inputRunnable = current.injector.instanceOf(clazz).asInstanceOf[InputRunnable[Any]]
        val mapping = GenericMapping[Any](inputRunnable.inputType)

        Form(mapping).bindFromRequest().fold(
          { formWithErrors =>
            BadRequest(adminviews.formFieldsInput(
              className.split('.').last, formWithErrors, routes.AdminController.runInputScript(className)
            ))
          },
          input => {
            inputRunnable.run(input)
            val execTimeSec = (new java.util.Date().getTime - start.getTime) / 1000
            val message = s"Script ${className} was successfully executed in ${execTimeSec} sec."
            messageLogger.info(message)
            runnablesRedirect.flashing("success" -> message)
          }
        )
      } catch {
        case e: ClassNotFoundException =>
          runnablesRedirect.flashing("errors" -> s"Script ${className} does not exist.")

        case e: Exception =>
          logger.error(s"Script ${className} failed", e)
          runnablesRedirect.flashing("errors" -> s"Script ${className} failed due to: ${e.getMessage}")
      }
    }
  }

  def importLdapUsers = restrictAdminAnyNoCaching(deadbolt) {
    implicit request =>
      userManager.synchronizeRepos.map ( _ =>
        mainRedirect.flashing("success" -> "LDAP users successfully imported.")
      )
  }

  def purgeMissingLdapUsers = restrictAdminAnyNoCaching(deadbolt) {
    implicit request =>
      userManager.purgeMissing.map ( _ =>
        mainRedirect.flashing("success" -> "Missing users successfully purged.")
      )
  }
}