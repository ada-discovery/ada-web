package org.ada.web.controllers.core

import org.incal.core.Identity
import org.incal.core.dataaccess.AsyncCrudRepo
import org.incal.play.controllers.{CrudControllerImpl, ReadonlyControllerImpl}
import play.api.libs.json.{Format, JsObject, Json}

abstract class AdaReadonlyControllerImpl[E: Format, ID] extends ReadonlyControllerImpl[E, ID] with AdaExceptionHandler

abstract class AdaCrudControllerImpl[E: Format, ID](
  repo: AsyncCrudRepo[E, ID])(
  implicit identity: Identity[E, ID]
) extends CrudControllerImpl[E, ID](repo) with AdaExceptionHandler