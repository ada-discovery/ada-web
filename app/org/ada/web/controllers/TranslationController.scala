package org.ada.web.controllers

import javax.inject.Inject

import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.server.models.Translation
import org.ada.server.models.Translation._
import org.incal.play.controllers.{AdminRestrictedCrudController, CrudControllerImpl, HasBasicFormCrudViews}
import org.ada.server.dataaccess.RepoTypes._
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText}
import play.api.mvc.{Request, Result}
import reactivemongo.bson.BSONObjectID
import views.html.{translation => view}

class TranslationController @Inject() (
    translationRepo: TranslationRepo
  ) extends AdaCrudControllerImpl[Translation, BSONObjectID](translationRepo)
    with AdminRestrictedCrudController[BSONObjectID]
    with HasBasicFormCrudViews[Translation, BSONObjectID] {

  override protected[controllers] val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "original" -> nonEmptyText,
      "translated" -> nonEmptyText
    )(Translation.apply)(Translation.unapply))

  override protected val homeCall = routes.TranslationController.find()
  override protected def createView = { implicit ctx => view.create(_) }
  override protected def showView = editView
  override protected def editView = { implicit ctx => view.edit(_) }
  override protected def listView = { implicit ctx => (view.list(_, _)).tupled }
}