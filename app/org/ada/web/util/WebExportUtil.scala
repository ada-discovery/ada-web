package org.ada.web.util

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import org.ada.server.dataaccess.JsonUtil
import org.apache.commons.lang3.StringEscapeUtils
import play.api.libs.json.JsObject
import play.api.http.HeaderNames._
import org.ada.server.dataaccess.JsonUtil.{jsonToDelimitedString, jsonsToCsv}
import play.api.http.{HttpChunk, HttpEntity}
import play.api.mvc.{ResponseHeader, Result, Results}
import akka.stream.scaladsl.StreamConverters

object WebExportUtil {

  private val DEFAULT_CHARSET = "UTF-8"

  def jsonStreamToCsvFile(
    source: Source[JsObject, _],
    fieldNames: Traversable[String],
    filename: String,
    delimiter: String = ",",
    eol: String = "\n",
    replacements: Traversable[(String, String)] = Nil,
    charset : String = DEFAULT_CHARSET
  ): Result = {
    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)
    val unescapedEOL = StringEscapeUtils.unescapeJava(eol)

    // create a header
    def headerFieldName(fieldName: String) = JsonUtil.unescapeKey(replaceAll(replacements)(fieldName))
    val header = fieldNames.map(headerFieldName).mkString(unescapedDelimiter)

    // transform each json to a delimited string and create a stream
    val contentStream = source.map(json => jsonToDelimitedString(json, fieldNames, unescapedDelimiter, replacements))

    val stringStream = Source.single(header).concat(contentStream).intersperse(unescapedEOL)
    streamToFile(stringStream, filename, charset)
  }

  def jsonStreamToJsonFile(
    source: Source[JsObject, _],
    filename: String,
    charset: String = DEFAULT_CHARSET
  ): Result = {
    val stringStream = source.map(_.toString).intersperse("[",",\n","]")
    streamToFile(stringStream, filename, charset)
  }

  private def replaceAll(
    replacements: Traversable[(String, String)])(
    value : String
  ): String =
    replacements.foldLeft(value) { case (string, (from , to)) => string.replaceAll(from, to) }

  def stringToFile(
    content : String,
    filename: String,
    charset : String = DEFAULT_CHARSET
  ): Result = {
    val source = Source.single(content)
    streamToFile(source, filename, charset)
  }

  def streamToFile(
    source: Source[String, _],
    filename: String,
    charset: String = DEFAULT_CHARSET
  ): Result = {
    val byteStream = source.map(ByteString(_, charset))
    streamToFileChunked(byteStream, filename)
  }

  def streamToFile(
    source: Source[ByteString, _],
    filename: String
  ): Result =
    Result(
      header = ResponseHeader(200, Map(CONTENT_DISPOSITION -> s"attachment; filename=${filename}")),
      body = HttpEntity.Streamed(source, None, Some("application/x-download")) // source.via(Compression.gzip) Some(content.length)
    )

  def streamToFileChunked(
    source: Source[ByteString, _],
    filename: String
  ): Result = {
    val chunked = source.map(HttpChunk.Chunk.apply(_))

    Result(
      header = ResponseHeader(200, Map(CONTENT_DISPOSITION -> s"attachment; filename=${filename}")), // TRANSFER_ENCODING -> "identity"
      body = HttpEntity.Chunked(chunked, Some("application/x-download")) // source.via(Compression.gzip) Some(content.length)
    )
  }
}