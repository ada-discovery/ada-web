package org.ada.web.controllers

import play.api.{Configuration, Environment}
import play.api.inject.{Binding, Module}

/**
  * Hacky solution to pass play.http.context (if defined) to routes during an initialization.
  *
  * See: https://github.com/playframework/playframework/issues/4984
  * or https://github.com/playframework/playframework/issues/4977
  *
  * Once the referenced issue in Play framework is fixed, this class can be removed
  */
class FixRoutesPrefixUrl extends Module {

  override def bindings(
    environment: Environment,
    configuration: Configuration
  ): Seq[Binding[_]] = {
    configuration.getString("play.http.context").foreach(_root_.core.RoutesPrefix.setPrefix)
    Nil
  }
}
