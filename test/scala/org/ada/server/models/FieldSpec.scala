package scala.org.ada.server.models

import org.ada.server.models.{Field, FieldTypeId}
import org.ada.server.models.DataSetFormattersAndIds.fieldFormat
import org.scalatest._
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID

class FieldSpec extends AsyncFlatSpec {

  implicit override def executionContext = scala.concurrent.ExecutionContext.Implicits.global

  behavior of "Field"

  it should "be serializable" in {
    val id = BSONObjectID.parse("5dc029c50c00000f059e1ccd") getOrElse fail
    val field = Field(
      name = "fieldName",
      label = Some("fieldLabel"),
      fieldType = FieldTypeId.Boolean,
      isArray = false,
      enumValues = Map("A" -> "B"),
      displayDecimalPlaces = Some(2),
      displayTrueValue = Some("y"),
      displayFalseValue = Some("n"),
      aliases = List("fieldAlias1", "fieldAlias2")
    )
    val serial = Json.toJson(field).toString()
    assert(serial == "{\"name\":\"fieldName\",\"label\":\"fieldLabel\",\"fieldType\":\"Boolean\",\"isArray\":false,\"enumValues\":{\"A\":\"B\"},\"displayDecimalPlaces\":2,\"displayTrueValue\":\"y\",\"displayFalseValue\":\"n\",\"aliases\":[\"fieldAlias1\",\"fieldAlias2\"]}")
  }

  it should "be de-serializable" in {
    val serial = "{\"name\":\"fieldName\",\"label\":\"fieldLabel\",\"fieldType\":\"Boolean\",\"isArray\":false,\"enumValues\":{\"A\":\"B\"},\"displayDecimalPlaces\":2,\"displayTrueValue\":\"y\",\"displayFalseValue\":\"n\",\"aliases\":[\"fieldAlias1\",\"fieldAlias2\"]}"
    val json = Json.parse(serial)
    val field = Json.fromJson[Field](json) getOrElse fail
    assert(field.name == "fieldName")
    assert(field.label.getOrElse(fail) == "fieldLabel")
    assert(field.fieldType == FieldTypeId.Boolean)
    assert(!field.isArray)
    assert(field.enumValues.getOrElse("A", fail) == "B")
    assert(field.displayDecimalPlaces.getOrElse(fail) == 2)
    assert(field.displayTrueValue.getOrElse(fail) == "y")
    assert(field.displayFalseValue.getOrElse(fail) == "n")
  }
}
