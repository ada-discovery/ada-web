package org.ada.web.runnables

import java.text.SimpleDateFormat

trait RunnableFileOutput {

  var fileName = "ada-output"
  val output = new StringBuilder

  protected def addOutput(string: String): Unit =
    output ++= string

  protected def addOutputLine(string: String): Unit =
    addOutput(string + "\n")

  protected def timestamp =
    new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new java.util.Date)

  protected def setOutputFileName(fileName: String) =
    this.fileName = fileName
}