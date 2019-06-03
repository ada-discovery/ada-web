package org.ada.web.controllers.core

import org.ada.web.controllers.BSONObjectIDStringFormatter
import org.ada.server.AdaException
import org.ada.server.models.{FieldTypeId, FieldTypeSpec, StorageType}
import play.api.data.Forms._
import play.api.data.format.Formats._
import play.api.data.validation.Constraint
import org.incal.core.util.ReflectionUtil
import org.incal.core.util.ReflectionUtil._

import scala.reflect.runtime.universe._
import play.api.data._
import java.{util => ju}

import org.incal.play.formatters._
import play.api.data.format.Formatter
import reactivemongo.bson.BSONObjectID

import scala.collection.Traversable

private class GenericMapping[R, A](
    apply: Traversable[A] => R,
    unapply: R => Option[Traversable[A]],
    fs: Traversable[(String, Mapping[A])],
    val key: String = "",
    val constraints: Seq[Constraint[R]] = Nil
  ) extends Mapping[R] with ObjectMapping {

  private val fieldMappings = fs.map(f => f._2.withPrefix(f._1).withPrefix(key))

  def bind(data: Map[String, String]): Either[Seq[FormError], R] = {
    merge(fieldMappings.map(_.bind(data)).toSeq :_*) match {
      case Left(errors) => Left(errors)
      case Right(values) => {
        applyConstraints(apply(values.map(_.asInstanceOf[A])))
      }
    }
  }

  def unbind(value: R): Map[String, String] = {
    unapply(value).map { values =>
      val maps = (fieldMappings.toSeq, values.toSeq).zipped.map { case (field, value) =>
        field.unbind(value)
      }
      maps.fold(Map[String, String]()){ _ ++ _}
    }.getOrElse(Map.empty)
  }

  def unbindAndValidate(value: R): (Map[String, String], Seq[FormError]) = {
    unapply(value).map { values =>
      val maps = (fieldMappings.toSeq, values.toSeq).zipped.map { case (field, value) =>
        field.unbindAndValidate(value)
      }
      maps.fold((Map[String, String](), Seq[FormError]())){ case (a, b) =>
        (a._1 ++ b._1, a._2 ++ b._2)
      }
    }.getOrElse(Map.empty[String, String] -> Seq(FormError(key, "unbind.failed")))
  }

  def withPrefix(prefix: String): GenericMapping[R, A] = addPrefix(prefix).map(newKey =>
    new GenericMapping(apply, unapply, fs, newKey, constraints)
  ).getOrElse(this)

  def verifying(addConstraints: Constraint[R]*): GenericMapping[R, A] = {
    new GenericMapping(apply, unapply, fs, key, constraints ++ addConstraints.toSeq)
  }

  override val mappings: Seq[Mapping[_]] = // Seq(this) ++
    fieldMappings.flatMap { field =>
      if (field.isInstanceOf[GenericMapping[_, _]])
        field.mappings
      else
        Seq(field)
    }.toSeq
}

object GenericMapping {

  def apply[A](fs: Traversable[(String, Mapping[A])]): Mapping[Traversable[A]] =
    new GenericMapping[Traversable[A], A](identity[Traversable[A]], Some(_), fs)

  def apply[T](typ: Type): Mapping[T] =
    if (isCaseClass(typ))
      applyCaseClass(typ)
    else
      applySimpleType(typ)

  def applySimpleType[T](typ: Type): Mapping[T] = {
    val currentMirror = newMirror(currentThreadClassLoader)

    genericMapping(typ, currentMirror).asInstanceOf[Mapping[T]]
  }

  def applyCaseClass[T](
    typ: Type,
    explicitMappings: Traversable[(String, Mapping[_])] = Nil
  ): Mapping[T] = {
    val cls = currentThreadClassLoader
    val currentMirror = newMirror(cls)

    val explicitMappingsMap = explicitMappings.asInstanceOf[Traversable[(String, Mapping[Any])]].toMap
    val mappings = caseClassMapping(typ, currentMirror, explicitMappingsMap)
    // ugly but somehow class information could be lost it the process (if a runtime type is used), hence we need to use a current thread class loader
    // TODO: try to replace with ReflectionUtil.classNameToRuntimeType and typeToClass
    val clazz = Class.forName(typ.typeSymbol.fullName, true, cls).asInstanceOf[Class[T]]

    new GenericMapping[T, Any](
      values => construct[T](clazz, values.toSeq),
      item => Some(item.asInstanceOf[Product].productIterator.toSeq),
      mappings
    )
  }

  private def caseClassMapping(
    typ: Type,
    mirror: Mirror,
    explicitMappings: Map[String, Mapping[Any]]
  ): Traversable[(String, Mapping[Any])] = {
    val memberNamesAndTypes = getCaseClassMemberNamesAndTypes(typ)

    memberNamesAndTypes.map {
      case (fieldName, memberType) =>
        val mapping =
          try {
            explicitMappings.get(fieldName).getOrElse(
              genericMapping(memberType, mirror)
            )
          } catch {
            case e: AdaException => failover(memberType, e)
          }
        (fieldName, mapping)
      }
  }

  // helper method to recover if a given member type cannot be recognized
  @throws(classOf[AdaException])
  private def failover(memberType: Type, e: AdaException): Mapping[Any] =
    // check if it's the member type is a case class and apply recurrently
    if (isCaseClass(memberType)) {
      GenericMapping.applyCaseClass[Any](memberType)
    } else if (memberType <:< typeOf[Option[_]]) {
      val typ = memberType.typeArgs.head

      // if it's an option type continue with its inner type (for a case class)
      if (isCaseClass(typ)) {
        val mapping = GenericMapping.applyCaseClass[Any](typ)
        optional(mapping).asInstanceOf[Mapping[Any]]
      } else {
        // otherwise ignore
        ignored(None)
      }
    } else
      throw e

  private implicit class Infix(val typ: Type) {
    def matches(types: Type*) = types.exists(typ =:= _)

    def subMatches(types: Type*) = types.exists(typ <:< _)
  }

  private implicit val bsonObjectIdFormatter = BSONObjectIDStringFormatter

  private implicit val stringSeqFormatter = SeqFormatter.apply
  private implicit val intSeqFormatter = SeqFormatter.asInt
  private implicit val doubleSeqFormatter = SeqFormatter.asDouble
  private implicit val bsonObjectIdSeqFormatter = BSONObjectIDSeqFormatter.apply

  private def getJavaEnumOrdinalValues[E <: Enum[E]](enumType: Type): Map[Int, String] = {
    val clazz = typeToClass(enumType).asInstanceOf[Class[E]]
    val enumValues = javaEnumOrdinalValues(clazz)
    enumValues.map { case (ordinal, value) => (ordinal, value.toString) }
  }

  @throws(classOf[AdaException])
  private def genericMapping(typ: Type, mirror: Mirror): Mapping[Any] = {
    val mapping = typ match {
      // float
      case t if t matches typeOf[Float] =>
        of[Float]

      // double
      case t if t matches typeOf[Double] =>
        of[Double]

      // bigdecimal
      case t if t matches typeOf[BigDecimal] =>
        bigDecimal

      // short
      case t if t matches typeOf[Short] =>
        shortNumber

      // byte
      case t if t matches typeOf[Byte] =>
        byteNumber

      // int
      case t if t matches typeOf[Int] =>
        number

      // long
      case t if t matches typeOf[Long] =>
        longNumber

      // boolean
      case t if t matches typeOf[Boolean] =>
        boolean

      // enum
      case t if t subMatches typeOf[Enumeration#Value] =>
        val enum = ReflectionUtil.enum(t, mirror)
        of(EnumFormatter(enum))

      // Java enum
      case t if t subMatches typeOf[Enum[_]] =>
        val clazz = typeToClass(t, mirror)
        of(JavaEnumMapFormatter(clazz))

      // string
      case t if t matches typeOf[String] =>
        nonEmptyText

      // date
      case t if t matches typeOf[ju.Date] =>
        date

      // BSON Object Id
      case t if t matches typeOf[BSONObjectID] =>
        of[BSONObjectID]

      // optional
      case t if t <:< typeOf[Option[_]] =>
        optional(genericMapping(t.typeArgs.head, mirror))

      // string seq
      case t if t subMatches typeOf[Seq[String]] =>
        of[Seq[String]]

      // int seq
      case t if t subMatches typeOf[Seq[Int]] =>
        of[Seq[Int]]

      // double seq
      case t if t subMatches typeOf[Seq[Double]] =>
        of[Seq[Double]]

      // BSON Object id seq
      case t if t subMatches typeOf[Seq[BSONObjectID]] =>
        of[Seq[BSONObjectID]]

      // seq
      case t if t subMatches typeOf[Seq[_]] =>
        val innerType = t.typeArgs.head
        seq(genericMapping(innerType, mirror))

      // set
      case t if t subMatches typeOf[Set[_]] =>
        val innerType = t.typeArgs.head
        set(genericMapping(innerType, mirror))

      // list
      case t if t subMatches typeOf[List[_]] =>
        val innerType = t.typeArgs.head
        list(genericMapping(innerType, mirror))

      // traversable seq
      case t if t subMatches typeOf[Traversable[String]] =>
        of[Seq[String]]

      // traversable
      case t if t subMatches typeOf[Traversable[_]] =>
        val innerType = t.typeArgs.head
        seq(genericMapping(innerType, mirror))

      // tuple2
      case t if t subMatches typeOf[Tuple2[_, _]] =>
        val typeArgs = t.typeArgs
        val mapping1 = genericMapping(typeArgs(0), mirror)
        val mapping2 = genericMapping(typeArgs(1), mirror)
        tuple(("1" -> mapping1), ("2" -> mapping2))

      // either valuer or seq (int)
      case t if t subMatches typeOf[Either[Option[Int], Seq[Int]]] =>
        of(EitherSeqFormatter[Int])

      // either valuer or seq (double)
      case t if t subMatches typeOf[Either[Option[Double], Seq[Double]]] =>
        of(EitherSeqFormatter[Double])

      // otherwise
      case _ =>
        val typeName =
          if (typ <:< typeOf[Option[_]])
            s"Option[${typ.typeArgs.head.typeSymbol.fullName}]"
          else
            typ.typeSymbol.fullName
        throw new AdaException(s"Mapping for type ${typeName} unknown.")
    }

    mapping.asInstanceOf[Mapping[Any]]
  }
}

case class TestCaseClass(name: String, label: Option[String], age: Int, storageType: Option[StorageType.Value])

object ObjectListMappingTest extends App {
  val mapping = GenericMapping.applyCaseClass[TestCaseClass](typeOf[TestCaseClass])
  mapping.mappings.foreach(mapping =>
    println(mapping)
  )

  val simpleMapping = GenericMapping.apply[String](typeOf[String])
  println(simpleMapping)
}