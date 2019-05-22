package org.ada.web.services

import org.incal.core.runnables.RunnableHtmlOutput
import play.twirl.api.Html

trait RunnableHtmlOutputExt extends RunnableHtmlOutput {

  protected def addHtml(html: Html): Unit =
    output ++= html.toString()
}
