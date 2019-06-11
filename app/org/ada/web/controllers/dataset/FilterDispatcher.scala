package org.ada.web.controllers.dataset

import be.objectify.deadbolt.scala.DeadboltHandler
import javax.inject.Inject
import org.ada.web.controllers.core.AdminOrOwnerControllerDispatcherExt
import org.ada.server.AdaException
import org.ada.server.models.Filter
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.mvc.{Action, AnyContent, Request}
import reactivemongo.bson.BSONObjectID
import org.incal.core.FilterCondition

import scala.concurrent.ExecutionContext.Implicits.global

class FilterDispatcher @Inject()(
  val dscf: DataSetControllerFactory,
  factory: FilterControllerFactory,
  dsaf: DataSetAccessorFactory
) extends DataSetLikeDispatcher[FilterController](ControllerName.filter)
   with AdminOrOwnerControllerDispatcherExt[FilterController]
   with FilterController {

  override def controllerFactory = factory(_)

  override def get(id: BSONObjectID) = dispatchIsAdminOrOwner(id, _.get(id))

  override def find(page: Int, orderBy: String, filter: Seq[FilterCondition]) = dispatch(_.find(page, orderBy, filter))

  override def listAll(orderBy: String) = dispatch(_.listAll(orderBy))

  override def create = dispatch(_.create)

  override def update(id: BSONObjectID) = dispatchIsAdminOrOwner(id, _.update(id))

  override def edit(id: BSONObjectID) = dispatchIsAdminOrOwner(id, _.edit(id))

  override def delete(id: BSONObjectID) = dispatchIsAdminOrOwner(id, _.delete(id))

  override def save = dispatch(_.save)

  override def saveAjax(filter: Filter) = dispatchAjax(_.saveAjax(filter))

  override def idAndNames = dispatchIsAdmin(_.idAndNames)

  override def idAndNamesAccessible  = dispatchAjax(_.idAndNamesAccessible)

  protected def dispatchIsAdminOrOwner(
    id: BSONObjectID,
    action: FilterController => Action[AnyContent],
    outputHandler: DeadboltHandler = handlerCache()
  ): Action[AnyContent] = {

    val objectOwnerFun = {
      request: Request[AnyContent] =>
        val dataSetId = getControllerId(request)
        val dsa = dsaf(dataSetId).getOrElse(throw new AdaException(s"Data set id $dataSetId not found."))
        dsa.filterRepo.get(id).map { filter =>
          filter.flatMap(_.createdById)
        }
    }

    dispatchIsAdminOrOwnerAux(objectOwnerFun, outputHandler)(action)
  }
}