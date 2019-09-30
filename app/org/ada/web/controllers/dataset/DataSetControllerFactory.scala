package org.ada.web.controllers.dataset

import com.google.inject.ImplementedBy
import org.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import org.ada.server.util.ClassFinderUtil.findClasses
import org.incal.core.util.toHumanReadableCamel
import play.api.inject.Injector
import javax.inject.{Inject, Singleton}
import org.ada.server.AdaException
import scala.concurrent.duration._

import collection.mutable.{Map => MMap}
import play.api.{Configuration, Logger}
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.{Await, duration}
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
        dsaf(dataSetId).map { dsa =>
          val controllerFuture = createController(dsa).map { controller =>
            cache.put(dataSetId, controller)
            controller
          }

          Await.result(controllerFuture, 30 seconds)
        }
    }
  }

  private def createController(dsa: DataSetAccessor) =
    dsa.setting.map(setting => createControllerAux(dsa.dataSetId, setting.customControllerClassName))

  private def createControllerAux(
    dataSetId: String,
    customControllerClassName: Option[String]
  ) =
    customControllerClassName.map { className =>
      findControllerClass(className).map { controllerClass =>
        injector.instanceOf(controllerClass)
      }.getOrElse {
        logger.warn(s"Controller class '$className' for the data set '$dataSetId' not found or doesn't implement DataSetController trait. Creating a generic one instead...")
        genericFactory(dataSetId)
      }
    }.getOrElse {
      logger.info(s"Creating a generic controller for the data set '$dataSetId' ...")
      genericFactory(dataSetId)
    }

  private def findControllerClass(controllerClassName: String): Option[Class[DataSetController]] =
    findClasses[DataSetController](Some("org.ada.web.controllers")).find(_.getName.equals(controllerClassName))
}