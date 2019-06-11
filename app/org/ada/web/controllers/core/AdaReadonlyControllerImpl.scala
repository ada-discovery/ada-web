package org.ada.web.controllers.core

import org.ada.web.models.security.DeadboltUser
import org.incal.core.Identity
import org.incal.core.dataaccess.AsyncCrudRepo
import org.incal.play.controllers.{BaseController, CrudControllerImpl, ReadonlyControllerImpl}
import play.api.libs.json.Format

abstract class AdaReadonlyControllerImpl[E: Format, ID] extends ReadonlyControllerImpl[E, ID] with AdaExceptionHandler {
  override type USER = DeadboltUser
}

abstract class AdaCrudControllerImpl[E: Format, ID](
  repo: AsyncCrudRepo[E, ID])(
  implicit identity: Identity[E, ID]
) extends CrudControllerImpl[E, ID](repo) with AdaExceptionHandler {
  override type USER = DeadboltUser
}

trait AdaBaseController extends BaseController {
  override type USER = DeadboltUser
}