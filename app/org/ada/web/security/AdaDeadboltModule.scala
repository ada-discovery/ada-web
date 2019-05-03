package org.ada.web.security

import be.objectify.deadbolt.scala.cache.HandlerCache
import be.objectify.deadbolt.scala.{DeadboltExecutionContextProvider, DeadboltModule, TemplateFailureListener}
import play.api.{Configuration, Environment}

/**
  * Extension of Deadbolt module with some custom/Ada stuff
  */
class AdaDeadboltModule extends DeadboltModule {

  override def bindings(environment: Environment, configuration: Configuration) =
    super.bindings(environment, configuration) ++ Seq(
      bind[TemplateFailureListener].to[AdaTemplateFailureListener],
      bind[HandlerCache].to[CustomHandlerCacheImpl],
      bind[DeadboltExecutionContextProvider].to[AdaDeadboltExecutionContextProvider]
    )
}
