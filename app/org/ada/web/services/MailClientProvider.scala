package org.ada.web.services

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import play.api.Configuration
import play.api.libs.mailer._

@ImplementedBy(classOf[MailClientProviderImpl])
trait MailClientProvider {
  def enabled(): Boolean
  def createClient(): MailerClient
  def createTemplate(subject: String, recipients: Seq[String], bodyText: Option[String]): Email
}

@Singleton
class MailClientProviderImpl  @Inject() (
    configuration: Configuration
  ) extends MailClientProvider {

  val mailerHost: Option[String] = configuration.getString("play.mailer.host")
  val mailerUser: Option[String] = configuration.getString("play.mailer.user")
  val systemMail: Option[String] = configuration.getString("play.mailer.systemmail")

  val SMTPConfig = new SMTPConfiguration(
    host = mailerHost.getOrElse("smtp-1.uni.lu"),
    port = configuration.getInt("play.mailer.port").getOrElse(587),
    ssl = configuration.getBoolean("play.mailer.ssl").getOrElse(false),
    tls = configuration.getBoolean("play.mailer.tls").getOrElse(false),
    user = mailerUser,
    password = configuration.getString("play.mailer.password"),
    debugMode = configuration.getBoolean("play.mailer.debug").getOrElse(true),
    timeout = None,
    connectionTimeout = None,
    mock = configuration.getBoolean("play.mailer.mock").getOrElse(true)
  )

  override def enabled(): Boolean = {
    (mailerHost.isDefined) && (mailerUser.isDefined) && (systemMail.isDefined)
  }

  /**
    * Generates a mailer client with values form documentation.
    * @return Generated SMTPMailer client.
    */
  override def createClient(): MailerClient = new SMTPMailer(SMTPConfig)

  /**
    * Create mail template with subject and sender generated generated or partiallz from config.
    * @param subject Subject of mail.
    * @param recipients Mail recipients.
    * @param bodyText Text of mail body.
    * @return Generated Email template.
    */
  override def createTemplate(subject: String, recipients: Seq[String], bodyText: Option[String]): Email = {
    new Email(
      "[Ada Reporting System] " + subject,
      systemMail.get,
      recipients,
      attachments = Seq(),
      bodyText = bodyText,
      bodyHtml = None
    )
  }
}