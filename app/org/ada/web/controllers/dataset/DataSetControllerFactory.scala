package org.ada.web.controllers.dataset

import com.google.inject.ImplementedBy
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.util.ClassFinderUtil.findClasses
import org.ada.web.util.toHumanReadableCamel
import play.api.inject.Injector
import javax.inject.{Inject, Singleton}

import collection.mutable.{Map => MMap}
import play.api.{Configuration, Logger}
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.reflect.ClassTag

@ImplementedBy(classOf[DataSetControllerFactoryImpl])
trait DataSetControllerFactory {
  def apply(dataSetId: String): Option[DataSetController]
}

@Singleton
protected class DataSetControllerFactoryImpl @Inject()(
    dsaf: DataSetAccessorFactory,
    genericFactory: GenericDataSetControllerFactory,
    injector : Injector,
    configuration: Configuration
  ) extends DataSetControllerFactory {

  private val logger = Logger  // (this.getClass())
  protected val cache = MMap[String, DataSetController]()

  // TODO: locking and concurrency
  override def apply(dataSetId: String): Option[DataSetController] = {
    cache.get(dataSetId) match {
      case Some(controller) => Some(controller)
      case None =>
        dsaf(dataSetId).map { _ =>
          val controller = createController(dataSetId)
          cache.put(dataSetId, controller)
          controller
        }
    }
  }

  private def createController(dataSetId: String) = {
    val controllerClass = findControllerClass[DataSetController](dataSetId)
    if (controllerClass.isDefined)
      injector.instanceOf(controllerClass.get)
    else {
      logger.info(s"Controller class for the data set id '$dataSetId' not found. Creating a generic one...")
      genericFactory(dataSetId)
    }
  }

  private def controllerClassName(dataSetId: String) = toHumanReadableCamel(dataSetId).replace(" ", "") + "Controller"

  private def findControllerClass[T : ClassTag](dataSetId: String): Option[Class[T]] = {
    val className = controllerClassName(dataSetId)
    findClasses[T](Some("controllers")).find(_.getSimpleName.equals(className))
  }
}