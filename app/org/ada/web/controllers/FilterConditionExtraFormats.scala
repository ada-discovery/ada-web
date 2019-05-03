package org.ada.web.controllers

import org.ada.server.models.Filter.FilterOrId
import org.incal.core.{ConditionType, FilterCondition}
import org.ada.server.json.{EitherFormat, EnumFormat}
import play.api.libs.json.{Format, __}
import play.api.libs.functional.syntax._
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

object FilterConditionExtraFormats {
  implicit val conditionTypeFormat = EnumFormat(ConditionType)

  // filter without value label
  implicit val coreFilterConditionFormat: Format[FilterCondition] = (
    (__ \ "fieldName").format[String] and
    (__ \ "fieldLabel").formatNullable[String] and
    (__ \ "conditionType").format[ConditionType.Value] and
    (__ \ "value").formatNullable[String]
  )(
    FilterCondition(_, _, _, _, None),
    (item: FilterCondition) =>  (item.fieldName, item.fieldLabel, item.conditionType, item.value)
  )

  implicit val eitherFilterOrIdFormat: Format[FilterOrId] = EitherFormat[Seq[FilterCondition], BSONObjectID]
}