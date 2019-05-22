package org.ada.web.controllers.core

import org.incal.play.formatters.SeqFormatter
import reactivemongo.bson.BSONObjectID

object BSONObjectIDSeqFormatter {
  def apply = new SeqFormatter[BSONObjectID](BSONObjectID.parse(_).toOption)
}
