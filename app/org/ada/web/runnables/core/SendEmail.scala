package org.ada.web.runnables.core

import javax.inject.Inject
import org.ada.server.AdaException
import org.incal.core.runnables.{InputRunnable, InputRunnableExt}
import play.api.Configuration
import play.api.libs.mailer.{Email, MailerClient}

class SendEmail @Inject()(mailerClient: MailerClient, configuration: Configuration) extends InputRunnableExt[SendEmailSpec] {

  override def run(input: SendEmailSpec) = {

    if (configuration.getString("play.mailer.host").isEmpty) {
      throw new AdaException("Email cannot be sent. The configuration entry 'play.mailer.host' is not set.")
    }

    val email = Email(
      from = input.from,
      to = Seq(input.to),
      subject = input.subject,
      bodyText = Some(input.body)
    )

    mailerClient.send(email)
  }
}

case class SendEmailSpec(
  from: String,
  to: String,
  subject: String,
  body: String
)