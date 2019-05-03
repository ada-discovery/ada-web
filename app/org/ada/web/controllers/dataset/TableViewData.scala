package org.ada.web.controllers.dataset

import org.incal.play.Page
import org.ada.server.models._
import play.api.libs.json.JsObject

case class TableViewData(
  page: Page[JsObject],
  filter: Option[Filter],
  tableFields: Traversable[Field]
)