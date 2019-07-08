package org.ada.web.runnables

import org.incal.core.runnables.InputRunnable
import org.incal.play.controllers.WebContext
import play.api.data.Form
import play.twirl.api.Html

trait InputView[I] {

  self: InputRunnable[I] =>

  def inputFields(implicit context: WebContext): Form[I] => Html

  protected def html(htmls: Html*): Html =
    Html(htmls.map(_.toString()).reduceLeft{_+_})
}
