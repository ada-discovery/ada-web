package org.ada.web.runnables

import java.text.SimpleDateFormat

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.ada.server.AdaException

trait RunnableFileOutput {

  var fileName = "ada-output"

  var outputByteSource: Option[Source[ByteString, _]] = None
  val output = new StringBuilder

  protected def timestamp =
    new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new java.util.Date)

  protected def setOutputFileName(fileName: String) =
    this.fileName = fileName

  protected def setOutputByteSource(outputByteSource: Source[ByteString, _]) =
    this.outputByteSource = Some(outputByteSource)

  protected def addOutput(string: String) =
    if (outputByteSource.isEmpty)
      output ++= string
    else
      throw new AdaException("Cannot add a string output since 'outputByteSource' has already been set.")

  protected def addOutputLine(string: String) =
    if (outputByteSource.isEmpty)
      addOutput(string + "\n")
    else
      throw new AdaException("Cannot add a string output since 'outputByteSource' has already been set.")
}