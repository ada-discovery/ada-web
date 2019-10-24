package scala.org.ada.server.services

import com.google.inject.Injector
import org.ada.server.services.GuicePlayTestApp
import net.codingwell.scalaguice.InjectorExtensions._

/**
 * Temporary injector wrapper to be used for testing until play has been factored out of ada-server
 */
object Injector {
  private val injector = GuicePlayTestApp().injector.instanceOf[Injector]
  def instanceOf[T: Manifest] = injector.instance[T]
}
