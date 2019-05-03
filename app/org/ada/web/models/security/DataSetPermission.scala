package org.ada.web.models.security

import org.ada.web.controllers.dataset.ControllerName

object DataSetPermission {

  def apply(
    dataSetId: String,
    controllerName: ControllerName.Value,
    actionName: String
  ) = "\\bDS:" + dataSetId.replaceAll("\\.","\\\\.") + "(\\." + controllerName.toString + "(\\." + actionName + ")?)?\\b"
}